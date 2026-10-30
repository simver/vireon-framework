package com.simxe.vireon.web.context;

import com.simxe.vireon.core.http.VireonRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class VireonRequestTraceId {

    private VireonRequestTraceId() {}

    public static String currentTraceId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        Object traceId = request.getAttribute(VireonRequestAttributes.TRACE_ID);
        if (traceId instanceof String traceIdValue && !traceIdValue.isBlank()) {
            return traceIdValue;
        }
        return null;
    }
}
