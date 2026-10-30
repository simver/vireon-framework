package com.simxe.vireon.testsupport;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.simxe.vireon.web.api.VireonApiResponse;
import java.util.concurrent.Callable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;

class VireonControllerContractAssertionsTests {

    @ParameterizedTest
    @ValueSource(strings = {"explicit", "entity", "async", "nested", "emptyData"})
    void acceptsExplicitContracts(String name) throws Exception {
        var method = Examples.class.getDeclaredMethod(name);
        assertThatCode(() -> VireonControllerContractAssertions.assertExplicitResponse(method)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"rawDto", "rawEnvelope", "rawEntity", "rawCallable", "unknown", "rawData", "noBody"})
    void rejectsImplicitOrUnresolvedContracts(String name) throws Exception {
        var method = Examples.class.getDeclaredMethod(name);
        assertThatThrownBy(() -> VireonControllerContractAssertions.assertExplicitResponse(method))
                .isInstanceOf(AssertionError.class).hasMessageContaining(name);
    }

    @SuppressWarnings("rawtypes")
    interface Examples {
        VireonApiResponse<String> explicit();
        ResponseEntity<VireonApiResponse<String>> entity();
        Callable<VireonApiResponse<String>> async();
        Callable<ResponseEntity<VireonApiResponse<String>>> nested();
        VireonApiResponse<Void> emptyData();
        String rawDto();
        VireonApiResponse rawEnvelope();
        ResponseEntity rawEntity();
        Callable rawCallable();
        VireonApiResponse<?> unknown();
        VireonApiResponse<java.util.List> rawData();
        ResponseEntity<Void> noBody();
    }
}
