package com.simxe.vireon.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.simxe.vireon.core.http.VireonRequestAttributes;
import com.simxe.vireon.web.api.VireonApiResponse;
import com.simxe.vireon.web.api.VireonResponses;
import com.simxe.vireon.web.error.VireonGlobalExceptionHandler;
import com.simxe.vireon.web.response.VireonResponseProtocolAdvice;
import jakarta.servlet.http.Cookie;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.HashSet;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import org.springframework.validation.beanvalidation.MethodValidationAdapter;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.HandlerMapping;

class VireonValidationMetadataTests {

    enum Mode {
        MVC, MVC_CGLIB, MVC_JDK, AOP_CGLIB, AOP_JDK, ADAPTED_CGLIB, ADAPTED_JDK;

        boolean aop() { return name().startsWith("AOP") || name().startsWith("ADAPTED"); }
        boolean adapted() { return name().startsWith("ADAPTED"); }
        boolean jdk() { return name().endsWith("JDK"); }
        Class<? extends Exception> exceptionType() {
            return !aop() ? HandlerMethodValidationException.class
                    : adapted() ? MethodValidationException.class : ConstraintViolationException.class;
        }
    }

    private final ValidatorFactory validators = Validation.buildDefaultValidatorFactory();

    @AfterEach
    void closeValidators() { validators.close(); }

    private MockMvc mvc(Mode mode) {
        Object controller = mode.aop() ? new AopController() : new NativeController();
        if (mode != Mode.MVC) {
            ProxyFactory factory = new ProxyFactory(controller);
            factory.setProxyTargetClass(!mode.jdk());
            factory.addAdvice(mode.aop() ? new MethodValidationInterceptor(validators::getValidator, mode.adapted())
                    : (MethodInterceptor) invocation -> invocation.proceed());
            controller = factory.getProxy();
            assertThat(AopUtils.isJdkDynamicProxy(controller)).isEqualTo(mode.jdk());
            assertThat(AopUtils.isCglibProxy(controller)).isEqualTo(!mode.jdk());
        }
        return build(controller);
    }

    private MockMvc build(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new VireonResponseProtocolAdvice(), new VireonGlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void interfaceParameterNameSurvivesEveryEntrypointAndProxy(Mode mode) throws Exception {
        var mvc = mvc(mode);
        mvc.perform(get("/metadata/count").param("external-count", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(2));
        assertInput(mvc, mode, get("/metadata/count").param("external-count", "0"), "external-count");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void sharesHttpAnnotationNamesAndAliases(Mode mode) throws Exception {
        var mvc = mvc(mode);
        assertInput(mvc, mode, get("/metadata/header").header("external-limit", 0), "external-limit");
        assertInput(mvc, mode, get("/metadata/path/0"), "external-id");
        assertInput(mvc, mode, get("/metadata/cookie").cookie(new Cookie("external-size", "0")), "external-size");
        assertInput(mvc, mode, get("/metadata/matrix/item;external-number=0"), "external-number");
        assertInput(mvc, mode, multipart("/metadata/part").file(new MockMultipartFile(
                "external-part", "", "application/json", "0".getBytes(StandardCharsets.UTF_8))), "external-part");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void resolvesGenericBridgeMethodWithoutLosingItsInterfaceContract(Mode mode) throws Exception {
        assertThat(List.of(NativeController.class.getDeclaredMethods())).anyMatch(java.lang.reflect.Method::isBridge);
        var mvc = mvc(mode);
        mvc.perform(get("/metadata/generic").param("external-value", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(2));
        assertInput(mvc, mode, get("/metadata/generic").param("external-value", "0"), "external-value");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void keepsSafeReturnClassificationAndExistingOutputPolicy(Mode mode) throws Exception {
        var mvc = mvc(mode);
        mvc.perform(get("/metadata/return"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("VIREON.COMMON.INTERNAL_ERROR"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
        for (String accept : List.of("text/plain", "application/json;q=0, */*;q=1", "broken")) {
            mvc.perform(get("/metadata/count").param("external-count", "0").header("Accept", accept))
                    .andExpect(status().isNotAcceptable()).andExpect(content().string(""))
                    .andExpect(header().string("Accept", "application/json"))
                    .andExpect(header().doesNotExist("Content-Type"));
        }
        mvc.perform(head("/metadata/count").param("external-count", "0").accept("text/plain"))
                .andExpect(status().isBadRequest()).andExpect(content().string(""));
    }

    @Test
    void movingAnnotationsToInterfaceOrRenamingImplementationDoesNotChangePublicName() throws Exception {
        for (Object controller : List.of(new DirectController(), new NativeController(),
                new RenamedController(), new InheritedController())) {
            var result = build(controller).perform(get("/metadata/count").param("external-count", "0"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data[0].path").value("external-count"))
                    .andReturn();
            assertThat(result.getResolvedException()).isInstanceOf(HandlerMethodValidationException.class);
        }
    }

    @Test
    void discoversImplicitNamesFromSpringMethodParameters() throws Exception {
        build(new DirectController()).perform(get("/metadata/implicit").param("count", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data[0].path").value("count"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sameNameOrSharedInterfaceDoesNotProveCurrentRequestOrigin(boolean adapted) throws Exception {
        var controller = new NativeController();
        var request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new HandlerMethod(controller, NativeController.class.getMethod("count", int.class)));
        var previous = RequestContextHolder.getRequestAttributes();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            for (Object target : List.of(controller, new InheritedController(), new NativeController())) {
                var method = target == controller ? NativeController.class.getMethod("count", String.class)
                        : NativeController.class.getMethod("count", int.class);
                Object[] arguments = target == controller ? new Object[] {""} : new Object[] {0};
                var handler = new VireonGlobalExceptionHandler();
                var result = adapted ? handler.handleMethodValidation(new MethodValidationException(
                        new MethodValidationAdapter(validators.getValidator())
                                .validateArguments(target, method, null, arguments, new Class<?>[0])))
                        : handler.handleConstraintViolation(new ConstraintViolationException(
                                validators.getValidator().forExecutables().validateParameters(target, method, arguments)));
                assertThat(result.getStatusCode().value()).isEqualTo(500);
                assertThat(result.getBody().code()).isEqualTo("VIREON.COMMON.INTERNAL_ERROR");
                assertThat(result.getBody().data()).isNull();
                assertThat(result.getBody().message()).doesNotContain("private");
            }
        } finally {
            RequestContextHolder.setRequestAttributes(previous);
        }
    }

    @Test
    void mixedRequestAndInternalViolationsRemainSafeServerErrors() throws Exception {
        var controller = new NativeController();
        var method = NativeController.class.getMethod("count", int.class);
        var executable = validators.getValidator().forExecutables();
        var violations = new HashSet<ConstraintViolation<?>>(
                executable.validateParameters(controller, method, new Object[] {0}));
        violations.addAll(executable.validateParameters(controller,
                NativeController.class.getMethod("count", String.class), new Object[] {""}));
        assertThat(violations).hasSize(2);
        var request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, new HandlerMethod(controller, method));
        var previous = RequestContextHolder.getRequestAttributes();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            var result = new VireonGlobalExceptionHandler().handleConstraintViolation(
                    new ConstraintViolationException(violations));
            assertThat(result.getStatusCode().value()).isEqualTo(500);
            assertThat(result.getBody().code()).isEqualTo("VIREON.COMMON.INTERNAL_ERROR");
            assertThat(result.getBody().data()).isNull();
            assertThat(result.getBody().message()).doesNotContain("private");
        } finally {
            RequestContextHolder.setRequestAttributes(previous);
        }
    }

    private void assertInput(MockMvc mvc, Mode mode, MockHttpServletRequestBuilder request, String externalName)
            throws Exception {
        mvc.perform(request.requestAttr(VireonRequestAttributes.TRACE_ID, "metadata-trace"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VIREON.COMMON.VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].path").value(externalName))
                .andExpect(jsonPath("$.data[0].code").value("VIREON.VALIDATION.MIN"))
                .andExpect(jsonPath("$.traceId").value("metadata-trace"))
                .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(mode.exceptionType()));
    }

    public interface GenericApi<T extends Number> {
        @GetMapping("/metadata/generic")
        VireonApiResponse<T> generic(@RequestParam("external-value") @Min(1) T value);
    }

    @RestController
    public interface Api extends GenericApi<Integer> {
        @GetMapping("/metadata/count")
        VireonApiResponse<Integer> count(@RequestParam("external-count") @Min(1) int value);

        @GetMapping("/metadata/header")
        VireonApiResponse<Integer> header(@RequestHeader(name = "external-limit") @Min(1) int value);

        @GetMapping("/metadata/path/{external-id}")
        VireonApiResponse<Integer> path(@PathVariable("external-id") @Min(1) int value);

        @GetMapping("/metadata/cookie")
        VireonApiResponse<Integer> cookie(@CookieValue(name = "external-size") @Min(1) int value);

        @GetMapping("/metadata/matrix/{segment}")
        VireonApiResponse<Integer> matrix(@MatrixVariable("external-number") @Min(1) int value);

        @PostMapping("/metadata/part")
        VireonApiResponse<Integer> part(@RequestPart(name = "external-part") @Min(1) Integer value);

        @GetMapping("/metadata/return")
        @NotBlank(message = "private return")
        String invalidReturn();
    }

    @RestController
    @Validated
    public interface ValidatedApi extends Api {}

    @RestController
    public static class NativeController implements Api {
        public VireonApiResponse<Integer> count(int count) { return VireonResponses.ok(count); }
        public void count(@NotBlank(message = "private overload") String internal) {}
        public VireonApiResponse<Integer> header(int limit) { return VireonResponses.ok(limit); }
        public VireonApiResponse<Integer> path(int id) { return VireonResponses.ok(id); }
        public VireonApiResponse<Integer> cookie(int size) { return VireonResponses.ok(size); }
        public VireonApiResponse<Integer> matrix(int number) { return VireonResponses.ok(number); }
        public VireonApiResponse<Integer> part(Integer part) { return VireonResponses.ok(part); }
        public VireonApiResponse<Integer> generic(Integer count) { return VireonResponses.ok(count); }
        public String invalidReturn() { return ""; }
    }

    @Validated
    public static class AopController extends NativeController implements ValidatedApi {}

    public static class RenamedController extends NativeController {
        @Override
        public VireonApiResponse<Integer> count(int renamed) { return VireonResponses.ok(renamed); }
    }

    public static class InheritedController extends NativeController {}

    @RestController
    public static class DirectController {
        @GetMapping("/metadata/count")
        public VireonApiResponse<Integer> count(@RequestParam(name = "external-count") @Min(1) int count) {
            return VireonResponses.ok(count);
        }

        @GetMapping("/metadata/implicit")
        public VireonApiResponse<Integer> implicit(@RequestParam @Min(1) int count) { return VireonResponses.ok(count); }
    }
}
