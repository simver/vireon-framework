package com.simxe.vireon.testsupport;

import com.simxe.vireon.web.api.VireonApiResponse;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

public final class VireonMockMvcResultMatchers {

    private VireonMockMvcResultMatchers() {}

    public static ResultMatcher success() {
        return result -> {
            MockMvcResultMatchers.status().is2xxSuccessful().match(result);
            MockMvcResultMatchers.jsonPath("$.code").value(VireonApiResponse.SUCCESS_CODE).match(result);
            MockMvcResultMatchers.jsonPath("$.message").value(VireonApiResponse.SUCCESS_MESSAGE).match(result);
        };
    }

    public static ResultMatcher failure(int status, String code) {
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("status must be an HTTP error status");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        return result -> {
            MockMvcResultMatchers.status().is(status).match(result);
            MockMvcResultMatchers.jsonPath("$.code").value(code).match(result);
            MockMvcResultMatchers.jsonPath("$.message").isNotEmpty().match(result);
        };
    }

    /** 已装配追踪能力的响应可组合此断言；通用信封允许没有 Trace ID。 */
    public static ResultMatcher traceIdPresent() {
        return MockMvcResultMatchers.jsonPath("$.traceId").isNotEmpty();
    }
}
