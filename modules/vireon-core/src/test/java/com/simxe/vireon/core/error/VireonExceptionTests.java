package com.simxe.vireon.core.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VireonExceptionTests {

    @Test
    void retainsStableErrorCodeAndSafeMessage() {
        VireonException exception =
                new VireonException(VireonCommonErrorCode.CONFLICT, "Account state has changed");

        assertThat(exception.errorCode()).isEqualTo(VireonCommonErrorCode.CONFLICT);
        assertThat(exception.getMessage()).isEqualTo("Account state has changed");
    }
}
