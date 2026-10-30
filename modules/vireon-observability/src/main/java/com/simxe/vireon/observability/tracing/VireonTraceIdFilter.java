package com.simxe.vireon.observability.tracing;

import com.simxe.vireon.core.http.VireonHeaders;
import com.simxe.vireon.core.http.VireonRequestAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public final class VireonTraceIdFilter extends OncePerRequestFilter {

    static final String CALLABLE_INTERCEPTOR_KEY = VireonTraceIdFilter.class.getName() + ".callable";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String traceId = resolveRequestTraceId(request);
        request.setAttribute(VireonRequestAttributes.TRACE_ID, traceId);
        response.setHeader(VireonHeaders.TRACE_ID, traceId);
        registerCallableInterceptor(request, traceId);
        try (VireonTraceIdContext.Scope ignored = VireonTraceIdContext.open(traceId)) {
            chain.doFilter(request, response);
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private static String resolveRequestTraceId(HttpServletRequest request) {
        Object existingTraceId = request.getAttribute(VireonRequestAttributes.TRACE_ID);
        if (existingTraceId instanceof String traceId && VireonTraceIdSupport.isValid(traceId)) {
            return traceId;
        }
        return VireonTraceIdSupport.resolve(request.getHeader(VireonHeaders.TRACE_ID));
    }

    private static void registerCallableInterceptor(HttpServletRequest request, String traceId) {
        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
        if (asyncManager.getCallableInterceptor(CALLABLE_INTERCEPTOR_KEY) == null) {
            asyncManager.registerCallableInterceptor(
                    CALLABLE_INTERCEPTOR_KEY, new TraceIdCallableProcessingInterceptor(traceId));
        }
    }

    private static final class TraceIdCallableProcessingInterceptor implements CallableProcessingInterceptor {

        private final String traceId;
        private final ThreadLocal<VireonTraceIdContext.Scope> scopes = new ThreadLocal<>();

        private TraceIdCallableProcessingInterceptor(String traceId) {
            this.traceId = traceId;
        }

        @Override
        public <T> void preProcess(NativeWebRequest request, Callable<T> task) {
            closeCurrentScope();
            scopes.set(VireonTraceIdContext.open(traceId));
        }

        @Override
        public <T> void postProcess(NativeWebRequest request, Callable<T> task, Object concurrentResult) {
            closeCurrentScope();
        }

        @Override
        public <T> void afterCompletion(NativeWebRequest request, Callable<T> task) {
            closeCurrentScope();
        }

        private void closeCurrentScope() {
            VireonTraceIdContext.Scope scope = scopes.get();
            if (scope != null) {
                scope.close();
                scopes.remove();
            }
        }
    }
}
