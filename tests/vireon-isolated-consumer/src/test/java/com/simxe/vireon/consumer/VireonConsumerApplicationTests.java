package com.simxe.vireon.consumer;

import static com.simxe.vireon.testsupport.VireonMockMvcResultMatchers.success;
import static com.simxe.vireon.testsupport.VireonMockMvcResultMatchers.traceIdPresent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.simxe.vireon.core.http.VireonHeaders;
import com.simxe.vireon.testsupport.VireonControllerContractAssertions;
import java.time.Clock;
import java.util.List;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.aop.proxy-target-class=false")
@AutoConfigureMockMvc
class VireonConsumerApplicationTests {

    @Test
    void declaresBusinessResponseTypesExplicitly() throws Exception {
        for (String method : new String[] {"smoke", "asyncSmoke"}) {
            VireonControllerContractAssertions.assertExplicitResponse(
                    VireonConsumerApplication.SmokeController.class.getDeclaredMethod(method));
        }
        for (Class<?> type : List.of(VireonConsumerApplication.ValidationController.class,
                VireonConsumerApplication.AopValidationController.class)) {
            VireonControllerContractAssertions.assertExplicitResponse(type.getMethod("count", int.class));
            VireonControllerContractAssertions.assertExplicitResponse(type.getMethod("people", List.class));
            VireonControllerContractAssertions.assertExplicitResponse(type.getMethod("matrix", List.class));
            VireonControllerContractAssertions.assertExplicitResponse(type.getMethod("peopleSet", java.util.Set.class));
        }
    }

    @Test
    void publishedArtifactDoesNotContainRetiredWrapper() {
        assertThat(getClass().getClassLoader().getResource(
                "com/simxe/vireon/web/response/VireonApiResponseBodyAdvice.class")).isNull();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Clock clock;

    @Autowired
    private VireonConsumerApplication.AopValidationApi aopController;

    @Test
    void publishedFrameworkKeepsInterfaceNamesForNativeAndJdkAopValidation() throws Exception {
        for (Class<?> type : List.of(VireonConsumerApplication.ValidationApi.class,
                VireonConsumerApplication.ValidationController.class, VireonConsumerApplication.AopValidationApi.class,
                VireonConsumerApplication.AopValidationController.class)) {
            assertThat(type.getMethod("count", int.class).getParameters()[0].isNamePresent()).isFalse();
        }
        assertThat(AopUtils.isJdkDynamicProxy(aopController)).isTrue();
        for (String path : List.of("/validation-smoke", "/aop-validation-smoke")) {
            mockMvc.perform(get(path).param("external-count", "2"))
                    .andExpect(success()).andExpect(traceIdPresent()).andExpect(jsonPath("$.data").value(2));
            mockMvc.perform(get(path).param("external-count", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VIREON.COMMON.VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.data[0].path").value("external-count"))
                    .andExpect(jsonPath("$.data[0].code").value("VIREON.VALIDATION.MIN"))
                    .andExpect(result -> jsonPath("$.traceId")
                            .value(result.getResponse().getHeader(VireonHeaders.TRACE_ID)).match(result))
                    .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(
                            path.startsWith("/aop") ? ConstraintViolationException.class
                                    : org.springframework.web.method.annotation.HandlerMethodValidationException.class));
        }
    }

    @Test
    void publishedValidationStillHonorsAcceptAndHead() throws Exception {
        mockMvc.perform(get("/aop-validation-smoke").param("external-count", "0")
                        .accept("application/json;q=0, */*;q=1"))
                .andExpect(status().isNotAcceptable())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""))
                .andExpect(header().string("Accept", "application/json"))
                .andExpect(header().doesNotExist("Content-Type"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head("/aop-validation-smoke")
                        .param("external-count", "0").accept("text/plain"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
    }

    @Test
    void publishedFrameworkRetainsObjectListPositions() throws Exception {
        assertThat(AopUtils.isJdkDynamicProxy(aopController)).isTrue();
        for (String path : List.of("/people-smoke", "/aop-people-smoke")) {
            mockMvc.perform(post(path).contentType("application/json").content("[{\"name\":\"ok\"}]"))
                    .andExpect(success()).andExpect(traceIdPresent()).andExpect(jsonPath("$.data[0].name").value("ok"));
            mockMvc.perform(post(path).contentType("application/json").content("[{\"name\":\"\"},{\"name\":\"\"}]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VIREON.COMMON.VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.data[*].path", org.hamcrest.Matchers.containsInAnyOrder("[0].name", "[1].name")))
                    .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(
                            path.startsWith("/aop") ? ConstraintViolationException.class
                                    : org.springframework.web.method.annotation.HandlerMethodValidationException.class))
                    .andExpect(result -> jsonPath("$.traceId")
                            .value(result.getResponse().getHeader(VireonHeaders.TRACE_ID)).match(result));
        }
    }

    @Test
    void publishedFrameworkRetainsUnindexedSetPaths() throws Exception {
        for (String path : List.of("/set-smoke", "/aop-set-smoke")) {
            mockMvc.perform(post(path).contentType("application/json").content("[{\"name\":\"\"}]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data[0].path").value("[].name"))
                    .andExpect(jsonPath("$.data[0].code").value("VIREON.VALIDATION.NOT_BLANK"))
                    .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(
                            path.startsWith("/aop") ? ConstraintViolationException.class
                                    : org.springframework.web.method.annotation.HandlerMethodValidationException.class))
                    .andExpect(result -> jsonPath("$.traceId")
                            .value(result.getResponse().getHeader(VireonHeaders.TRACE_ID)).match(result));
        }
    }

    @Test
    void publishedSpringHandlerKeepsAsyncApplicationFailuresSafeAndTraced() throws Exception {
        assertThat(com.simxe.vireon.web.error.VireonGlobalExceptionHandler.class)
                .isAssignableTo(org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler.class);
        for (String path : List.of("/stream-failure-smoke", "/callable-stream-failure-smoke")) {
            var result = mockMvc.perform(get(path).accept("application/json")).andExpect(request().asyncStarted()).andReturn();
            // Callable 返回流时有两次异步阶段，每次都等待实际结果，不用 sleep 或假定一次 dispatch。
            for (int dispatch = 0; dispatch < 3 && result.getRequest().isAsyncStarted(); dispatch++) {
                result.getAsyncResult(5000);
                result = mockMvc.perform(asyncDispatch(result)).andReturn();
            }
            assertThat(result.getRequest().isAsyncStarted()).isFalse();
            status().isInternalServerError().match(result);
            jsonPath("$.code").value("VIREON.COMMON.INTERNAL_ERROR").match(result);
            jsonPath("$.traceId").value(result.getResponse().getHeader(VireonHeaders.TRACE_ID)).match(result);
            assertThat(result.getResponse().getContentType()).isEqualTo("application/json");
            assertThat(result.getResponse().getContentAsString()).doesNotContain("private stream diagnostic");
            assertThat(result.getResolvedException()).isInstanceOf(java.io.IOException.class);
        }
    }

    @Test
    void consumesPublishedStarterAndTestSupport() throws Exception {
        assertThat(clock.getZone().getId()).isEqualTo("Asia/Shanghai");

        mockMvc.perform(get("/smoke"))
                .andExpect(success()).andExpect(traceIdPresent())
                .andExpect(header().exists(VireonHeaders.TRACE_ID))
                .andExpect(result -> jsonPath("$.traceId")
                        .value(result.getResponse().getHeader(VireonHeaders.TRACE_ID)).match(result))
                .andExpect(jsonPath("$.data.status").value("ready"));
    }

    @Test
    void publishedFrameworkRetainsDirectNestedContainersWithoutParameterNames() throws Exception {
        for (String path : List.of("/matrix-smoke", "/aop-matrix-smoke")) {
            mockMvc.perform(post(path).contentType("application/json").content("[[{\"name\":\"ok\"}]]"))
                    .andExpect(success()).andExpect(traceIdPresent()).andExpect(jsonPath("$.data[0][0].name").value("ok"));
            mockMvc.perform(post(path).contentType("application/json").content("[[{\"name\":\"\"}],[{\"name\":\"\"}]]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data[*].path", org.hamcrest.Matchers.containsInAnyOrder("[0][0].name", "[1][0].name")))
                    .andExpect(jsonPath("$.data[*].code", org.hamcrest.Matchers.everyItem(
                            org.hamcrest.Matchers.is("VIREON.VALIDATION.NOT_BLANK"))))
                    .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(
                            path.startsWith("/aop") ? ConstraintViolationException.class
                                    : org.springframework.web.method.annotation.HandlerMethodValidationException.class))
                    .andExpect(result -> jsonPath("$.traceId")
                            .value(result.getResponse().getHeader(VireonHeaders.TRACE_ID)).match(result));
        }
    }

    @Test
    void keepsActuatorHealthOutsideBusinessEnvelope() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void keepsTraceIdAcrossPublishedStarterAsyncExecution() throws Exception {
        String traceId = "0123456789abcdef0123456789abcdef";
        MvcResult initialResult = mockMvc.perform(get("/async-smoke").header(VireonHeaders.TRACE_ID, traceId))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(initialResult))
                .andExpect(status().isOk())
                .andExpect(header().string(VireonHeaders.TRACE_ID, traceId))
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.data.traceId").value(traceId));
    }
}
