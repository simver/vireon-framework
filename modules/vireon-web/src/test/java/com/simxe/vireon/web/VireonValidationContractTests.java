package com.simxe.vireon.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.simxe.vireon.core.http.VireonRequestAttributes;
import com.simxe.vireon.web.api.VireonApiResponse;
import com.simxe.vireon.web.api.VireonResponses;
import com.simxe.vireon.web.error.VireonGlobalExceptionHandler;
import com.simxe.vireon.web.response.VireonResponseProtocolAdvice;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraintvalidation.SupportedValidationTarget;
import jakarta.validation.constraintvalidation.ValidationTarget;
import java.lang.annotation.*;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import org.springframework.web.bind.annotation.*;

@ExtendWith(OutputCaptureExtension.class)
class VireonValidationContractTests {

    enum Mode { MVC, AOP, ADAPTED_AOP }

    private final ValidatorFactory validators = Validation.buildDefaultValidatorFactory();

    @AfterEach
    void closeValidators() {
        validators.close();
    }

    private <T> T proxy(T target, boolean adapted) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new MethodValidationInterceptor(validators::getValidator, adapted));
        @SuppressWarnings("unchecked")
        T result = (T) factory.getProxy();
        return result;
    }

    private MockMvc mvc(Mode mode) {
        return mvc(mode, true);
    }

    private MockMvc mvc(Mode mode, boolean bindingValidation) {
        var service = proxy(new CheckedService(), mode == Mode.ADAPTED_AOP);
        Object controller = mode == Mode.MVC ? new Controller(service, validators)
                : proxy(new AopController(service, validators), mode == Mode.ADAPTED_AOP);
        assertThat(org.springframework.aop.support.AopUtils.isCglibProxy(controller)).isEqualTo(mode != Mode.MVC);
        var builder = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new VireonResponseProtocolAdvice(), new VireonGlobalExceptionHandler());
        if (!bindingValidation) {
            // 隔离 AOP 的真实级联校验路径，避免 MVC 绑定校验先行中止请求。
            builder.setValidator(new org.springframework.validation.Validator() {
                @Override
                public boolean supports(Class<?> type) { return true; }
                @Override
                public void validate(Object target, org.springframework.validation.Errors errors) {}
            });
        }
        return builder.build();
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void keepsFieldObjectAndNestedErrorsAcrossEntrypoints(Mode mode) throws Exception {
        var mvc = mvc(mode);
        for (String path : List.of("/validation/bean", "/validation/method")) {
            for (var sample : List.of(
                    new String[] {"{\"name\":\"\"}", "name", "NOT_BLANK"},
                    new String[] {"{\"name\":\"invalid\"}", "input", "CONSISTENT_INPUT"},
                    new String[] {"{\"name\":\"ok\",\"child\":{\"name\":\"\"}}", "child.name", "NOT_BLANK"})) {
                mvc.perform(post(path).contentType("application/json").content(sample[0])
                                .requestAttr(VireonRequestAttributes.TRACE_ID, "validation-trace"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VIREON.COMMON.VALIDATION_FAILED"))
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].path").value(sample[1]))
                        .andExpect(jsonPath("$.data[0].code").value("VIREON.VALIDATION." + sample[2]))
                        .andExpect(jsonPath("$.data[0].message").isNotEmpty())
                        .andExpect(jsonPath("$.traceId").value("validation-trace"));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void retainsHttpParameterNamesAndMultipleErrors(Mode mode) throws Exception {
        mvc(mode).perform(post("/validation/method?external-count=0")
                        .contentType("application/json").content("{\"name\":\"ok\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0].path").value("external-count"))
                .andExpect(jsonPath("$.data[0].code").value("VIREON.VALIDATION.MIN"));
        mvc(mode).perform(post("/validation/method")
                        .contentType("application/json").content("{\"name\":\"\",\"child\":{\"name\":\"\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].path", org.hamcrest.Matchers.containsInAnyOrder("name", "child.name")))
                .andExpect(jsonPath("$.data[*].code", org.hamcrest.Matchers.containsInAnyOrder(
                        "VIREON.VALIDATION.NOT_BLANK", "VIREON.VALIDATION.NOT_BLANK")));
    }

    @ParameterizedTest
    @EnumSource(value = Mode.class, names = {"AOP", "ADAPTED_AOP"})
    void normalizesActualAopCascadedErrors(Mode mode) throws Exception {
        var mvc = mvc(mode, false);
        for (var sample : List.of(
                new String[] {"{\"name\":\"\"}", "name"},
                new String[] {"{\"name\":\"invalid\"}", "input"},
                new String[] {"{\"name\":\"ok\",\"child\":{\"name\":\"\"}}", "child.name"})) {
            mvc.perform(post("/validation/method").contentType("application/json").content(sample[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data[0].path").value(sample[1]))
                    .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(
                            mode == Mode.AOP ? ConstraintViolationException.class
                                    : org.springframework.validation.method.MethodValidationException.class));
        }
        mvc.perform(post("/validation/method?external-count=0")
                        .contentType("application/json").content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void returnsSafeServerErrorForControllerReturnAndInternalValidation(Mode mode, CapturedOutput output) throws Exception {
        var mvc = mvc(mode);
        for (String path : List.of("/validation/return", "/validation/service-return",
                "/validation/service-input", "/validation/manual", "/validation/other-method")) {
            mvc.perform(get(path).requestAttr(VireonRequestAttributes.TRACE_ID, "server-validation-trace"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("VIREON.COMMON.INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
        }
        assertThat(output).contains("Unhandled request exception, traceId=server-validation-trace");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void sharesAcceptAndHeadPolicyForInputAndServerValidation(Mode mode) throws Exception {
        var mvc = mvc(mode);
        for (String accept : List.of("application/json;q=0, */*;q=1", "text/plain", "broken")) {
            mvc.perform(post("/validation/method").contentType("application/json").content("{\"name\":\"\"}")
                            .header("Accept", accept))
                    .andExpect(status().isNotAcceptable()).andExpect(content().string(""))
                    .andExpect(header().string("Accept", "application/json"))
                    .andExpect(header().doesNotExist("Content-Type"));
            mvc.perform(get("/validation/service-return").header("Accept", accept))
                    .andExpect(status().isNotAcceptable()).andExpect(content().string(""));
        }
        mvc.perform(head("/validation/return").accept("text/plain"))
                .andExpect(status().isInternalServerError()).andExpect(content().string(""));
        mvc.perform(head("/validation/scalar?external-count=0").accept("text/plain"))
                .andExpect(status().isBadRequest()).andExpect(content().string(""));
    }

    @ParameterizedTest
    @EnumSource(value = Mode.class, names = {"AOP", "ADAPTED_AOP"})
    void retainsCrossParameterErrors(Mode mode) throws Exception {
        // 6.2 的适配式方法校验仅有跨参数错误时 hasErrors() 为 false；混合结果用于验证出口不丢明细。
        mvc(mode).perform(get("/validation/cross?left=2&right=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[*].path", org.hamcrest.Matchers.hasItem("<cross-parameter>")))
                .andExpect(jsonPath("$.data[*].code", org.hamcrest.Matchers.hasItem("VIREON.VALIDATION.ORDERED")));
        if (mode == Mode.AOP) {
            mvc(mode).perform(get("/validation/cross?left=2&right=1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data[0].path").value("<cross-parameter>"));
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void retainsContainerElementPath(Mode mode) throws Exception {
        mvc(mode).perform(get("/validation/container").param("items", "ok", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0].path").value("items[1]"))
                .andExpect(jsonPath("$.data[0].code").value("VIREON.VALIDATION.NOT_BLANK"));
    }

    @Test
    void keepsCrossOnlyAdaptedResultEvenWhenSpringHasErrorsIgnoresIt() throws Exception {
        var controller = new Controller(new CheckedService(), validators);
        var method = Controller.class.getMethod("cross", int.class, int.class);
        var result = new org.springframework.validation.beanvalidation.MethodValidationAdapter(validators.getValidator())
                .validateArguments(controller, method, null, new Object[] {2, 1}, new Class<?>[0]);
        assertThat(result.getCrossParameterValidationResults()).hasSize(1);
        // 记录依赖行为边界：hasErrors=false 导致 AOP 不抛异常，但不能让本层也丢弃已收到的跨参数结果。
        assertThat(result.hasErrors()).isFalse();
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new org.springframework.web.method.HandlerMethod(controller, method));
        var previous = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            var response = new VireonGlobalExceptionHandler().handleMethodValidation(
                    new org.springframework.validation.method.MethodValidationException(result));
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody().data()).asList().hasSize(1);
        } finally {
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(previous);
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void retainsObjectListElementAndNestedFieldLocations(Mode mode) throws Exception {
        assertContainerPaths(mode, "/validation/people", """
                [{"name":""},{"name":""}]
                """, "[0].name", "[1].name");
        assertContainerPaths(mode, "/validation/people", """
                [{"name":""},{"name":"ok","child":{"name":""}}]
                """, "[0].name", "[1].child.name");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void retainsObjectMapKeysAndNestedFieldLocations(Mode mode) throws Exception {
        assertContainerPaths(mode, "/validation/people-map", """
                {"alpha":{"name":""},"beta":{"name":"ok","child":{"name":""}}}
                """, "[alpha].name", "[beta].child.name");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void locatesListElementObjectErrors(Mode mode) throws Exception {
        assertContainerPaths(mode, "/validation/people", """
                [{"name":"invalid"},{"name":"invalid"}]
                """, "[0]", "[1]");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void locatesMapElementObjectErrors(Mode mode) throws Exception {
        assertContainerPaths(mode, "/validation/people-map", """
                {"alpha":{"name":"invalid"},"beta":{"name":"invalid"}}
                """, "[alpha]", "[beta]");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void retainsUnindexedSetFieldObjectAndNestedPaths(Mode mode) throws Exception {
        assertContainerPaths(mode, "/validation/people-set", """
                [{"name":""},{"name":"ok","child":{"name":""}}]
                """, "[].name", "[].child.name");
        assertContainerPaths(mode, "/validation/people-set", """
                [{"name":"invalid"}]
                """, "[]");
        mvc(mode, mode == Mode.MVC).perform(post("/validation/people-set")
                        .contentType("application/json").content("[{\"name\":\"ok\"}]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("ok"));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void preservesExistingNestedCollectionPathsAndValidRequests(Mode mode) throws Exception {
        assertContainerPaths(mode, "/validation/groups", """
                [{"people":[{"name":""},{"name":"ok","child":{"name":""}}]}]
                """, "[0].people[0].name", "[0].people[1].child.name");
        mvc(mode, mode == Mode.MVC).perform(post("/validation/people")
                        .contentType("application/json").content("[{\"name\":\"ok\"}]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("ok"));
    }

    private void assertContainerPaths(Mode mode, String path, String json, String... paths) throws Exception {
        // 只有专用 AOP 夹具隔离绑定校验，并断言实际异常，防止 MVC 提前拦截而掩盖兼容路径。
        mvc(mode, mode == Mode.MVC).perform(post(path).contentType("application/json").content(json)
                        .requestAttr(VireonRequestAttributes.TRACE_ID, "container-trace"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(switch (mode) {
                    case MVC -> org.springframework.web.method.annotation.HandlerMethodValidationException.class;
                    case AOP -> ConstraintViolationException.class;
                    case ADAPTED_AOP -> org.springframework.validation.method.MethodValidationException.class;
                }))
                .andExpect(jsonPath("$.code").value("VIREON.COMMON.VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data[*].path", org.hamcrest.Matchers.containsInAnyOrder(paths)))
                .andExpect(jsonPath("$.data[*].code", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(
                        json.contains("invalid") ? "VIREON.VALIDATION.CONSISTENT_INPUT" : "VIREON.VALIDATION.NOT_BLANK"))))
                .andExpect(jsonPath("$.data[*].message", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.isEmptyOrNullString()))))
                .andExpect(jsonPath("$.traceId").value("container-trace"));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void retainsDirectNestedListsIncludingObjectErrors(Mode mode) throws Exception {
        assertContainerPaths(mode, "/validation/matrix", """
                [[{"name":""},{"name":"ok","child":{"name":""}}],[{"name":""}]]
                """, "[0][0].name", "[0][1].child.name", "[1][0].name");
        assertContainerPaths(mode, "/validation/matrix", """
                [[{"name":"invalid"}],[{"name":"invalid"}]]
                """, "[0][0]", "[1][0]");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void retainsDirectMapListPathsIncludingObjectErrors(Mode mode) throws Exception {
        assertContainerPaths(mode, "/validation/map-list", """
                {"alpha":[{"name":""}],"beta":[{"name":"ok","child":{"name":""}}]}
                """, "[alpha][0].name", "[beta][0].child.name");
        assertContainerPaths(mode, "/validation/map-list", """
                {"alpha":[{"name":"invalid"}],"beta":[{"name":"invalid"}]}
                """, "[alpha][0]", "[beta][0]");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void retainsThreeContainerLevelsWithoutDuplicatingLocations(Mode mode) throws Exception {
        assertContainerPaths(mode, "/validation/cube", """
                [[[{"name":""},{"name":""}]]]
                """, "[0][0][0].name", "[0][0][1].name");
        mvc(mode, mode == Mode.MVC).perform(post("/validation/matrix")
                        .contentType("application/json").content("[[{\"name\":\"ok\"}]]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0][0].name").value("ok"));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void stableConstraintCodesDoNotRequireJavaParameterNames(Mode mode, @TempDir Path output) throws Exception {
        // 真实编译无参数名夹具，不用 mock 参数名来模拟编译器行为；不改变正常测试的编译设置。
        Path source = Path.of(getClass().getResource("/validation/NoParameterNamesController.java").toURI());
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        assertThat(compiler.run(null, null, null, "-proc:none", "-g:none", "-classpath",
                System.getProperty("java.class.path"), "-d", output.toString(), source.toString())).isZero();
        try (var loader = new URLClassLoader(new java.net.URL[] {output.toUri().toURL()}, getClass().getClassLoader())) {
            String name = "com.simxe.vireon.web.fixture.NoParameterNamesController";
            Class<?> type = loader.loadClass(mode == Mode.MVC ? name : name + "$Aop");
            assertThat(type.getMethod("count", int.class).getParameters()[0].isNamePresent()).isFalse();
            Object controller = type.getConstructor().newInstance();
            if (mode != Mode.MVC) {
                controller = proxy(controller, mode == Mode.ADAPTED_AOP);
                assertThat(org.springframework.aop.support.AopUtils.isCglibProxy(controller)).isTrue();
            }
            var mvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new VireonResponseProtocolAdvice(), new VireonGlobalExceptionHandler()).build();
            mvc.perform(get("/no-names/count").param("external-count", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data[0].path").value("external-count"))
                    .andExpect(jsonPath("$.data[0].code").value("VIREON.VALIDATION.MIN"))
                    .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(switch (mode) {
                        case MVC -> org.springframework.web.method.annotation.HandlerMethodValidationException.class;
                        case AOP -> ConstraintViolationException.class;
                        case ADAPTED_AOP -> org.springframework.validation.method.MethodValidationException.class;
                    }));
        }
    }

    @Test
    void unknownConstraintSourceIsNotAssumedToBeRequestInput() {
        var exception = new ConstraintViolationException(validators.getValidator().validate(new Input("", null)));
        assertThat(new VireonGlobalExceptionHandler().handleConstraintViolation(exception).getStatusCode().value())
                .isEqualTo(500);
    }

    @RestController
    public static class Controller {
        private final CheckedService service;
        private final ValidatorFactory validators;

        Controller(CheckedService service, ValidatorFactory validators) {
            this.service = service;
            this.validators = validators;
        }

        @PostMapping("/validation/bean")
        public VireonApiResponse<Input> bean(@Valid @RequestBody Input input) { return VireonResponses.ok(input); }

        @PostMapping("/validation/method")
        public VireonApiResponse<Input> method(@Valid @RequestBody Input input,
                @RequestParam(name = "external-count", defaultValue = "1") @Min(1) int count) {
            return VireonResponses.ok(input);
        }

        @GetMapping("/validation/scalar")
        public VireonApiResponse<Integer> scalar(@RequestParam("external-count") @Min(1) int count) {
            return VireonResponses.ok(count);
        }

        @GetMapping("/validation/container")
        public VireonApiResponse<List<String>> container(@RequestParam("items") @Size(min = 1) List<@NotBlank String> items) {
            return VireonResponses.ok(items);
        }

        @GetMapping("/validation/return")
        @NotBlank(message = "private controller return")
        public String invalidReturn() { return ""; }

        @PostMapping("/validation/people")
        public VireonApiResponse<List<Input>> people(@RequestBody @Size(min = 1) @Valid List<Input> people) {
            return VireonResponses.ok(people);
        }

        @PostMapping("/validation/people-map")
        public VireonApiResponse<Map<String, Input>> peopleMap(
                @RequestBody @Size(min = 1) @Valid Map<String, Input> people) {
            return VireonResponses.ok(people);
        }

        @PostMapping("/validation/people-set")
        public VireonApiResponse<java.util.Set<Input>> peopleSet(
                @RequestBody @Size(min = 1) @Valid java.util.Set<Input> people) {
            return VireonResponses.ok(people);
        }

        @PostMapping("/validation/groups")
        public VireonApiResponse<List<Group>> groups(@RequestBody @Size(min = 1) @Valid List<Group> groups) {
            return VireonResponses.ok(groups);
        }

        @PostMapping("/validation/matrix")
        public VireonApiResponse<List<List<Input>>> matrix(
                @RequestBody @Size(min = 1) List<@Valid List<@Valid Input>> rows) {
            return VireonResponses.ok(rows);
        }

        @PostMapping("/validation/map-list")
        public VireonApiResponse<Map<String, List<Input>>> mapList(
                @RequestBody @Size(min = 1) Map<String, @Valid List<@Valid Input>> rows) {
            return VireonResponses.ok(rows);
        }

        @PostMapping("/validation/cube")
        public VireonApiResponse<List<List<List<Input>>>> cube(
                @RequestBody @Size(min = 1) List<@Valid List<@Valid List<@Valid Input>>> rows) {
            return VireonResponses.ok(rows);
        }

        @GetMapping("/validation/service-return")
        public VireonApiResponse<String> serviceReturn() { return VireonResponses.ok(service.invalidReturn()); }

        @GetMapping("/validation/service-input")
        public VireonApiResponse<String> serviceInput() { return VireonResponses.ok(service.input("")); }

        @GetMapping("/validation/manual")
        public void manual() {
            throw new ConstraintViolationException(validators.getValidator().validate(new Input("", null)));
        }

        @GetMapping("/validation/other-method")
        public void otherMethod() throws Exception {
            throw new ConstraintViolationException(validators.getValidator().forExecutables().validateParameters(
                    this, Controller.class.getMethod("scalar", int.class), new Object[] {0}));
        }

        @GetMapping("/validation/cross")
        @Ordered
        public VireonApiResponse<Void> cross(@RequestParam int left, @RequestParam @Min(0) int right) {
            return VireonResponses.ok();
        }
    }

    @Validated
    public static class AopController extends Controller {
        AopController(CheckedService service, ValidatorFactory validators) { super(service, validators); }
    }

    public static class CheckedService {
        @NotBlank(message = "private service return")
        public String invalidReturn() { return ""; }
        public String input(@NotBlank(message = "private service input") String value) { return value; }
    }

    @ConsistentInput
    public record Input(@NotBlank String name, @Valid Child child) {}
    public record Child(@NotBlank String name) {}
    public record Group(@Valid List<Input> people) {}

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = InputValidator.class)
    public @interface ConsistentInput {
        String message() default "Input is invalid";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class InputValidator implements ConstraintValidator<ConsistentInput, Input> {
        @Override
        public boolean isValid(Input input, ConstraintValidatorContext context) {
            return input == null || !"invalid".equals(input.name());
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = OrderedValidator.class)
    public @interface Ordered {
        String message() default "Invalid ordering";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @SupportedValidationTarget(ValidationTarget.PARAMETERS)
    public static class OrderedValidator implements ConstraintValidator<Ordered, Object[]> {
        @Override
        public boolean isValid(Object[] values, ConstraintValidatorContext context) {
            return (int) values[0] <= (int) values[1];
        }
    }
}
