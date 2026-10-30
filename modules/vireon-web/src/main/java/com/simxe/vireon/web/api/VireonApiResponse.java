package com.simxe.vireon.web.api;

import com.simxe.vireon.core.error.VireonErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@Schema(description = "Vireon 统一 API 响应")
public record VireonApiResponse<T>(
        @Schema(description = "稳定业务结果码", example = SUCCESS_CODE) String code,
        @Schema(description = "面向调用方的安全结果消息", example = SUCCESS_MESSAGE) String message,
        @Schema(description = "业务响应数据；无数据时为空") T data,
        @Schema(description = "规范化请求追踪标识；未建立追踪上下文时为空", example = "0123456789abcdef0123456789abcdef")
                String traceId) {

    public static final String SUCCESS_CODE = "VIREON.COMMON.SUCCESS";
    public static final String SUCCESS_MESSAGE = "OK";

    public VireonApiResponse {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public static <T> VireonApiResponse<T> success(T data, String traceId) {
        return new VireonApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data, traceId);
    }

    public static VireonApiResponse<Void> success(String traceId) {
        return success(null, traceId);
    }

    public static VireonApiResponse<Void> failure(VireonErrorCode errorCode, String traceId) {
        return failure(errorCode, errorCode.message(), null, traceId);
    }

    public static <T> VireonApiResponse<T> failure(
            VireonErrorCode errorCode, String message, T data, String traceId) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        return new VireonApiResponse<>(errorCode.code(), Objects.requireNonNull(message), data, traceId);
    }
}
