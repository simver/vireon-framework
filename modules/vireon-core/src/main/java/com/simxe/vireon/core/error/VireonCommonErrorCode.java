package com.simxe.vireon.core.error;

public enum VireonCommonErrorCode implements VireonErrorCode {
    BAD_REQUEST("VIREON.COMMON.BAD_REQUEST", "Bad request", 400),
    VALIDATION_FAILED("VIREON.COMMON.VALIDATION_FAILED", "Validation failed", 400),
    UNAUTHORIZED("VIREON.COMMON.UNAUTHORIZED", "Authentication required", 401),
    FORBIDDEN("VIREON.COMMON.FORBIDDEN", "Permission denied", 403),
    NOT_FOUND("VIREON.COMMON.NOT_FOUND", "Resource not found", 404),
    METHOD_NOT_ALLOWED("VIREON.COMMON.METHOD_NOT_ALLOWED", "Method not allowed", 405),
    CONFLICT("VIREON.COMMON.CONFLICT", "Request conflicts with current state", 409),
    UNSUPPORTED_MEDIA_TYPE("VIREON.COMMON.UNSUPPORTED_MEDIA_TYPE", "Unsupported media type", 415),
    INTERNAL_ERROR("VIREON.COMMON.INTERNAL_ERROR", "Internal server error", 500);

    private final String code;
    private final String message;
    private final int httpStatus;

    VireonCommonErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
