package com.simxe.vireon.web.error;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "请求参数校验错误")
public record VireonValidationError(
        @Schema(description = "校验失败的字段或对象路径", example = "name") String path,
        @Schema(description = "稳定校验错误码", example = "VIREON.VALIDATION.NOT_BLANK") String code,
        @Schema(description = "面向调用方的安全校验消息", example = "must not be blank") String message) {}
