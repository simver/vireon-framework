package com.simxe.vireon.core.error;

public interface VireonErrorCode {

    String code();

    String message();

    int httpStatus();
}
