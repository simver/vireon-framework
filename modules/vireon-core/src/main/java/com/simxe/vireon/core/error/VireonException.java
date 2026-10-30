package com.simxe.vireon.core.error;

import java.util.Objects;

public class VireonException extends RuntimeException {

    private final VireonErrorCode errorCode;

    public VireonException(VireonErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    public VireonException(VireonErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public VireonException(VireonErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public VireonErrorCode errorCode() {
        return errorCode;
    }
}
