package com.simxe.vireon.observability.tracing;

import java.util.UUID;
import java.util.regex.Pattern;

final class VireonTraceIdSupport {

    private static final String INVALID_ZERO_TRACE_ID = "00000000000000000000000000000000";
    private static final Pattern VALID_TRACE_ID = Pattern.compile("[0-9a-f]{32}");

    private VireonTraceIdSupport() {}

    static String resolve(String incomingTraceId) {
        return isValid(incomingTraceId) ? incomingTraceId : generate();
    }

    static boolean isValid(String traceId) {
        return traceId != null
                && !INVALID_ZERO_TRACE_ID.equals(traceId)
                && VALID_TRACE_ID.matcher(traceId).matches();
    }

    private static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
