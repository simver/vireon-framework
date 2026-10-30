package com.simxe.vireon.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.models.Operation;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

/** 标记仅代表处理方法及声明注解的Git历史，不代表调用链变更时间。 */
final class OpenApiGitCustomizer implements OperationCustomizer {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));
    private final JsonNode history;
    OpenApiGitCustomizer(JsonNode history) { this.history = history; }
    @Override public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (history == null) return operation;
        String handler = handlerMethod.getBeanType().getName() + "#" + handlerMethod.getMethod().getName();
        JsonNode match = null;
        for (JsonNode entry : history.get("operations")) if (handler.equals(entry.get("handler").asText())) { match = entry; break; }
        if (match == null || !match.get("operationId").asText().equals(operation.getOperationId())) throw new IllegalStateException("Missing or stale operation history mapping");
        String label = switch (match.get("state").asText()) {
            case "dirty" -> "未提交变更";
            case "committed" -> "处理方法更新 " + FORMAT.format(Instant.parse(match.get("timestamp").asText())) + " (Asia/Shanghai)";
            default -> "历史不可用";
        };
        operation.addExtension("x-badges", java.util.List.of(Map.of("name", label, "position", "after")));
        return operation;
    }
}
