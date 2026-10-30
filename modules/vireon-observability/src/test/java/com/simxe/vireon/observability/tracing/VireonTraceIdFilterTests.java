package com.simxe.vireon.observability.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import com.simxe.vireon.core.http.VireonHeaders;
import com.simxe.vireon.core.http.VireonRequestAttributes;
import jakarta.servlet.DispatcherType;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncUtils;

class VireonTraceIdFilterTests {

    @Test
    void forwardsValidTraceAndClearsThreadState() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(VireonHeaders.TRACE_ID, "0123456789abcdef0123456789abcdef");
        MockHttpServletResponse response = new MockHttpServletResponse();
        VireonTraceIdFilter filter = new VireonTraceIdFilter();

        filter.doFilter(request, response, (chainRequest, chainResponse) -> {
            assertThat(VireonTraceIdContext.currentTraceId()).contains("0123456789abcdef0123456789abcdef");
            assertThat(MDC.get(VireonTraceIdContext.MDC_KEY)).isEqualTo("0123456789abcdef0123456789abcdef");
        });

        assertThat(request.getAttribute(VireonRequestAttributes.TRACE_ID))
                .isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(response.getHeader(VireonHeaders.TRACE_ID)).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(VireonTraceIdContext.currentTraceId()).isEmpty();
        assertThat(MDC.get(VireonTraceIdContext.MDC_KEY)).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                " ",
                "00000000000000000000000000000000",
                "1234",
                "0123456789abcdef0123456789abcdeF",
                "01234567-89ab-cdef-0123-456789abcdef",
                "0123456789abcdef0123456789abcdef0"
            })
    void replacesMissingOrInvalidTrace(String incomingTraceId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (incomingTraceId != null) {
            request.addHeader(VireonHeaders.TRACE_ID, incomingTraceId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        new VireonTraceIdFilter().doFilter(request, response, (chainRequest, chainResponse) -> {
            String activeTraceId = VireonTraceIdContext.currentTraceId().orElseThrow();
            assertThat(activeTraceId).matches("[0-9a-f]{32}");
            assertThat(activeTraceId).isNotEqualTo("00000000000000000000000000000000");
            assertThat(MDC.get(VireonTraceIdContext.MDC_KEY)).isEqualTo(activeTraceId);
        });

        String generatedTraceId = (String) request.getAttribute(VireonRequestAttributes.TRACE_ID);
        assertThat(generatedTraceId).matches("[0-9a-f]{32}");
        assertThat(generatedTraceId).isEqualTo(response.getHeader(VireonHeaders.TRACE_ID));
        assertThat(VireonTraceIdContext.currentTraceId()).isEmpty();
        assertThat(MDC.get(VireonTraceIdContext.MDC_KEY)).isNull();
    }

    @Test
    void propagatesTraceIntoMvcCallableAndRestoresWorkerContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(VireonHeaders.TRACE_ID, "0123456789abcdef0123456789abcdef");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<CallableProcessingInterceptor> interceptorReference = new AtomicReference<>();

        new VireonTraceIdFilter().doFilter(request, response, (chainRequest, chainResponse) ->
                interceptorReference.set(WebAsyncUtils.getAsyncManager(request)
                        .getCallableInterceptor(VireonTraceIdFilter.CALLABLE_INTERCEPTOR_KEY)));

        CallableProcessingInterceptor interceptor = interceptorReference.get();
        Callable<Void> task = () -> null;
        ServletWebRequest webRequest = new ServletWebRequest(request, response);
        assertThat(interceptor).isNotNull();
        assertThat(VireonTraceIdContext.currentTraceId()).isEmpty();

        try (VireonTraceIdContext.Scope ignored =
                VireonTraceIdContext.open("abcdefabcdefabcdefabcdefabcdefab")) {
            interceptor.preProcess(webRequest, task);
            assertThat(VireonTraceIdContext.currentTraceId())
                    .contains("0123456789abcdef0123456789abcdef");
            assertThat(MDC.get(VireonTraceIdContext.MDC_KEY))
                    .isEqualTo("0123456789abcdef0123456789abcdef");

            interceptor.postProcess(webRequest, task, null);
            assertThat(VireonTraceIdContext.currentTraceId())
                    .contains("abcdefabcdefabcdefabcdefabcdefab");
            assertThat(MDC.get(VireonTraceIdContext.MDC_KEY))
                    .isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
        }
        assertThat(VireonTraceIdContext.currentTraceId()).isEmpty();
        assertThat(MDC.get(VireonTraceIdContext.MDC_KEY)).isNull();
    }

    @Test
    void reusesNormalizedRequestTraceAcrossAsyncDispatches() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                VireonRequestAttributes.TRACE_ID, "abcdef0123456789abcdef0123456789");
        request.setDispatcherType(DispatcherType.ASYNC);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new VireonTraceIdFilter().doFilter(request, response, (chainRequest, chainResponse) -> {
            assertThat(VireonTraceIdContext.currentTraceId())
                    .contains("abcdef0123456789abcdef0123456789");
            assertThat(MDC.get(VireonTraceIdContext.MDC_KEY))
                    .isEqualTo("abcdef0123456789abcdef0123456789");
        });

        assertThat(response.getHeader(VireonHeaders.TRACE_ID))
                .isEqualTo("abcdef0123456789abcdef0123456789");
        assertThat(VireonTraceIdContext.currentTraceId()).isEmpty();
    }
}
