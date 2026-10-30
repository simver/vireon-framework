package com.simxe.vireon.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;

import com.simxe.vireon.web.api.VireonResponses;
import com.simxe.vireon.web.error.VireonGlobalExceptionHandler;
import com.simxe.vireon.web.response.VireonResponseProtocolAdvice;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class VireonResponseProtocolTests {

    @AfterEach
    void clearContext() { RequestContextHolder.resetRequestAttributes(); }

    @ParameterizedTest
    @ValueSource(ints = {100, 103, 204, 205, 304})
    void suppressesBodiesForEverySelectedConverter(int status) throws Exception {
        var request = new MockHttpServletRequest("GET", "/protocol");
        var response = new MockHttpServletResponse();
        response.setStatus(status);
        var parameter = new MethodParameter(getClass().getDeclaredMethod("stream"), -1);
        var advice = new VireonResponseProtocolAdvice();
        for (var converter : List.of(StringHttpMessageConverter.class, FormHttpMessageConverter.class,
                MappingJackson2HttpMessageConverter.class)) {
            assertThat(advice.supports(parameter, converter)).isTrue();
            assertThat(advice.beforeBodyWrite(Map.of("unexpected", "body"), parameter, MediaType.APPLICATION_JSON,
                    converter, new ServletServerHttpRequest(request), new ServletServerHttpResponse(response))).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 103, 204, 205, 304})
    void suppressesBodiesThroughTheMvcConversionChain(int status) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new BodyController())
                .setControllerAdvice(new VireonResponseProtocolAdvice(), new VireonGlobalExceptionHandler()).build();
        for (String representation : List.of("json", "text", "form")) {
            var response = mvc.perform(get("/body")
                            .param("status", Integer.toString(status)).param("representation", representation))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).as(representation).isEqualTo(status);
            assertThat(response.getContentAsByteArray()).as(representation).isEmpty();
        }
    }

    @Test
    void suppressesHeadAcrossJsonTextAndFormConverters() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new BodyController())
                .setControllerAdvice(new VireonResponseProtocolAdvice(), new VireonGlobalExceptionHandler()).build();
        for (String representation : List.of("json", "text", "form")) {
            var response = mvc.perform(head("/body").param("status", "200").param("representation", representation))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).as(representation).isEqualTo(200);
            assertThat(response.getContentAsByteArray()).as(representation).isEmpty();
        }
    }

    @Test
    void explicitEmptySuccessDoesNotInventTraceContext() {
        assertThat(VireonResponses.ok().data()).isNull();
        assertThat(VireonResponses.ok().traceId()).isNull();
    }

    @Test
    void committedFailuresDoNotAppendAnotherResponse() throws Exception {
        var response = new MockHttpServletResponse();
        response.getWriter().write("partial");
        response.flushBuffer();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest(), response));
        Exception failure = new IllegalStateException("write failed");
        assertThat(new VireonGlobalExceptionHandler().handleException(failure)).isNull();
        assertThat(response.getContentAsString()).isEqualTo("partial");
    }

    @Test
    void availableStreamingResponseCanReportApplicationIoFailure() throws Exception {
        var request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new HandlerMethod(this, getClass().getDeclaredMethod("stream")));
        var response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        IOException failure = new IOException("stream unavailable");
        var result = new VireonGlobalExceptionHandler().handleException(failure);
        assertThat(result.getStatusCode().value()).isEqualTo(500);
        assertThat(result.getBody().message()).doesNotContain("stream unavailable");
        assertThat(response.getContentAsByteArray()).isEmpty();
        var unusable = new AsyncRequestNotUsableException("response unavailable");
        assertThat(new VireonGlobalExceptionHandler().handleException(unusable)).isNull();
        assertThat(new VireonGlobalExceptionHandler().handleException(new IllegalStateException(unusable))).isNull();
    }

    @Test
    void applicationIoFailureBeforeWritingStillGetsSafe500() throws Exception {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse()));
        var result = new VireonGlobalExceptionHandler().handleException(new IOException("private diagnostic"));
        assertThat(result.getStatusCode().value()).isEqualTo(500);
        assertThat(result.getBody().message()).doesNotContain("private diagnostic");
    }

    @Test
    void springOwnsEveryStandardMvcExceptionRegistration() throws Exception {
        var resolver = new ExceptionHandlerMethodResolver(VireonGlobalExceptionHandler.class);
        var springMethod = ResponseEntityExceptionHandler.class.getMethod("handleException", Exception.class,
                org.springframework.web.context.request.WebRequest.class);
        var registration = springMethod.getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler.class);
        for (var exceptionType : registration.value()) {
            assertThat(resolver.resolveMethodByExceptionType(exceptionType)).as(exceptionType.getName())
                    .isEqualTo(springMethod);
        }
    }

    @Test
    void actualOutputFailureUsesSpringUnusableSignalWithoutASecondWrite() throws Exception {
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        var servletResponse = new MockHttpServletResponse() {
            @Override
            public ServletOutputStream getOutputStream() {
                return new ServletOutputStream() {
                    public boolean isReady() { return true; }
                    public void setWriteListener(WriteListener listener) {}
                    public void write(int value) throws IOException {
                        writes.incrementAndGet();
                        throw new IOException("private transport failure");
                    }
                };
            }
        };
        var request = new MockHttpServletRequest();
        request.setAsyncSupported(true);
        var async = new StandardServletAsyncWebRequest(request, servletResponse);
        async.startAsync();
        var response = async.getResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        var failure = org.assertj.core.api.Assertions.catchThrowable(() -> response.getOutputStream().write(1));
        assertThat(failure).isInstanceOf(AsyncRequestNotUsableException.class).hasRootCauseMessage("private transport failure");
        assertThat(response.isCommitted()).isFalse();
        var handler = new VireonGlobalExceptionHandler();
        assertThat(handler.handleException((Exception) failure, new ServletWebRequest(request, response))).isNull();
        assertThat(handler.handleException(new IllegalStateException(failure))).isNull();
        assertThat(handler.handleVireonException(new com.simxe.vireon.core.error.VireonException(
                com.simxe.vireon.core.error.VireonCommonErrorCode.INTERNAL_ERROR, "safe message", failure))).isNull();
        assertThat(writes).hasValue(1);
        assertThat(servletResponse.getContentType()).isNull();
        // 确认 Spring 已标记响应不可用：再次调用也不会触达原始输出流。
        assertThatThrownBy(() -> response.getOutputStream().write(2)).isInstanceOf(AsyncRequestNotUsableException.class);
        assertThat(writes).hasValue(1);
    }

    StreamingResponseBody stream() { return output -> {}; }

    @RestController
    public static class BodyController {
        @GetMapping("/body")
        public ResponseEntity<?> body(@RequestParam int status, @RequestParam String representation) {
            return switch (representation) {
                case "text" -> ResponseEntity.status(status).contentType(MediaType.TEXT_PLAIN).body("unexpected");
                case "form" -> {
                    var form = new LinkedMultiValueMap<String, String>();
                    form.add("value", "unexpected");
                    yield ResponseEntity.status(status).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form);
                }
                default -> ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                        .body(VireonResponses.ok("unexpected"));
            };
        }
    }
}
