package com.simxe.vireon.observability.tracing;

import java.util.Optional;
import org.slf4j.MDC;

public final class VireonTraceIdContext {

    public static final String MDC_KEY = "traceId";

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private VireonTraceIdContext() {}

    static Scope open(String traceId) {
        Scope scope = new Scope(TRACE_ID.get(), MDC.get(MDC_KEY));
        TRACE_ID.set(traceId);
        MDC.put(MDC_KEY, traceId);
        return scope;
    }

    public static Optional<String> currentTraceId() {
        return Optional.ofNullable(TRACE_ID.get());
    }

    public static void clear() {
        TRACE_ID.remove();
        MDC.remove(MDC_KEY);
    }

    static final class Scope implements AutoCloseable {

        private final String previousTraceId;
        private final String previousMdcTraceId;
        private boolean closed;

        private Scope(String previousTraceId, String previousMdcTraceId) {
            this.previousTraceId = previousTraceId;
            this.previousMdcTraceId = previousMdcTraceId;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            restoreThreadLocal(previousTraceId);
            restoreMdc(previousMdcTraceId);
            closed = true;
        }

        private static void restoreThreadLocal(String traceId) {
            if (traceId == null) {
                TRACE_ID.remove();
            } else {
                TRACE_ID.set(traceId);
            }
        }

        private static void restoreMdc(String traceId) {
            if (traceId == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, traceId);
            }
        }
    }
}
