package com.simxe.vireon.testsupport;

import static com.simxe.vireon.testsupport.VireonMockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.simxe.vireon.web.api.VireonApiResponse;
import com.simxe.vireon.web.api.VireonResponses;
import com.simxe.vireon.web.error.VireonGlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class VireonMockMvcResultMatchersTests {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Controller())
            .setControllerAdvice(new VireonGlobalExceptionHandler()).build();

    @Test
    void acceptsLegitimateEnvelopesWithoutTracing() throws Exception {
        mvc.perform(get("/ok")).andExpect(success());
        mvc.perform(get("/conflict")).andExpect(failure(409, "CONFLICT"));
    }

    @Test
    void stillRejectsWrongStatusAndCode() throws Exception {
        var ok = mvc.perform(get("/ok")).andReturn();
        var conflict = mvc.perform(get("/conflict")).andReturn();
        assertThatThrownBy(() -> success().match(conflict)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> failure(409, "CONFLICT").match(ok)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> failure(409, "WRONG").match(conflict)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> failure(400, "CONFLICT").match(conflict)).isInstanceOf(AssertionError.class);
    }

    @Test
    void traceAssertionIsExplicitAndComposable() throws Exception {
        for (String path : new String[] {"/ok", "/conflict"}) {
            var result = mvc.perform(get(path)).andReturn();
            assertThatThrownBy(() -> traceIdPresent().match(result)).isInstanceOf(AssertionError.class);
        }
        mvc.perform(get("/ok").requestAttr(com.simxe.vireon.core.http.VireonRequestAttributes.TRACE_ID, "test-trace"))
                .andExpect(success()).andExpect(traceIdPresent());
    }

    @RestController
    static class Controller {
        @GetMapping("/ok")
        VireonApiResponse<String> ok() { return VireonResponses.ok("ready"); }

        @GetMapping("/conflict")
        ResponseEntity<VireonApiResponse<Void>> conflict() {
            return ResponseEntity.status(409).body(new VireonApiResponse<>("CONFLICT", "冲突", null, null));
        }
    }
}
