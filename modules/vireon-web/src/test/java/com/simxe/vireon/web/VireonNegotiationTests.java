package com.simxe.vireon.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.simxe.vireon.core.error.VireonCommonErrorCode;
import com.simxe.vireon.core.error.VireonException;
import com.simxe.vireon.web.api.VireonApiResponse;
import com.simxe.vireon.web.api.VireonResponses;
import com.simxe.vireon.web.error.VireonGlobalExceptionHandler;
import com.simxe.vireon.web.internal.VireonResponsePolicy;
import com.simxe.vireon.web.response.VireonResponseProtocolAdvice;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

class VireonNegotiationTests {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Controller())
            .setControllerAdvice(new VireonResponseProtocolAdvice(), new VireonGlobalExceptionHandler()).build();

    static Stream<Arguments> acceptCases() {
        return Stream.of(
                Arguments.of(List.of(), true),
                Arguments.of(List.of(" "), true),
                Arguments.of(List.of("*/*"), true),
                Arguments.of(List.of("application/*"), true),
                Arguments.of(List.of("application/json"), true),
                Arguments.of(List.of("APPLICATION/JSON;Q=1"), true),
                Arguments.of(List.of("application/json;q=0.001"), true),
                Arguments.of(List.of("application/json;q=0"), false),
                Arguments.of(List.of("application/json;q=0, */*;q=1"), false),
                Arguments.of(List.of("*/*;q=1, application/json;q=0"), false),
                Arguments.of(List.of("application/*;q=0, */*;q=1"), false),
                Arguments.of(List.of("application/json;q=1, application/*;q=0"), true),
                Arguments.of(List.of("application/json;q=0", "*/*;q=1"), false),
                Arguments.of(List.of("application/json;q=1", "application/json;q=0"), false),
                Arguments.of(List.of("application/json;q=0", "application/json;q=1"), false),
                Arguments.of(List.of("application/json;charset=UTF-8"), true),
                Arguments.of(List.of("application/json;charset=\"utf-8\";q=0, application/json;q=1"), false),
                Arguments.of(List.of("application/json;charset=ISO-8859-1"), false),
                Arguments.of(List.of("application/json;profile=other, */*;q=1"), true),
                Arguments.of(List.of("application/json;profile=other"), false),
                Arguments.of(List.of("application/vnd.example+json"), false),
                Arguments.of(List.of("application/*+json"), false),
                Arguments.of(List.of("text/plain"), false),
                Arguments.of(List.of("application/xml"), false),
                Arguments.of(List.of("broken"), false),
                Arguments.of(List.of("application/json;q=2"), false),
                Arguments.of(List.of("application/json;q=NaN"), false));
    }

    @ParameterizedTest
    @MethodSource("acceptCases")
    void candidatePolicyMatchesIndependentContract(List<String> accept, boolean allowed) {
        assertThat(VireonResponsePolicy.accepts(accept, MediaType.APPLICATION_JSON)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @MethodSource("acceptCases")
    void successAndAllFailureFamiliesUseTheSamePolicy(List<String> accept, boolean allowed) throws Exception {
        Map<String, Integer> statuses = Map.of(
                "/protocol/explicit", 200, "/protocol/business", 409,
                "/protocol/binding", 400, "/protocol/validation", 400,
                "/protocol/missing", 404, "/protocol/unknown", 500);
        for (var scenario : statuses.entrySet()) {
            MockHttpServletRequestBuilder request = scenario.getKey().endsWith("validation")
                    ? post(scenario.getKey()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")
                    : get(scenario.getKey());
            if (!accept.isEmpty()) {
                request.header(HttpHeaders.ACCEPT, accept.toArray());
            }
            var response = mvc.perform(request).andReturn().getResponse();
            assertThat(response.getStatus()).as(scenario.getKey() + " " + accept)
                    .isEqualTo(allowed ? scenario.getValue() : 406);
            if (allowed) {
                assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
                assertThat(response.getContentAsString()).contains("\"code\":").doesNotContain("private diagnostic");
            } else {
                assertThat(response.getContentAsByteArray()).isEmpty();
                assertThat(response.getContentType()).isNull();
                assertThat(response.getHeader(HttpHeaders.CONTENT_LENGTH)).isNull();
                assertThat(response.getHeader(HttpHeaders.ACCEPT)).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
            }
        }
    }

    @Test
    void matchesActualVendorTypeAndParametersWithoutTreatingJsonAsAnAlias() throws Exception {
        mvc.perform(get("/protocol/vendor").accept("application/vnd.example+json"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.example+json"));
        mvc.perform(get("/protocol/vendor").accept("application/vnd.example+json;q=0, application/*+json;q=1"))
                .andExpect(status().isNotAcceptable()).andExpect(content().string(""));
        mvc.perform(get("/protocol/vendor").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotAcceptable());
        MediaType candidate = MediaType.parseMediaType("application/json;profile=\"v1\"");
        assertThat(VireonResponsePolicy.accepts(List.of("application/json;profile=v1"), candidate)).isTrue();
        assertThat(VireonResponsePolicy.accepts(
                List.of("application/json;profile=v1;q=0, application/json;q=1"), candidate)).isFalse();
    }

    @Test
    void clearsPreviousRepresentationOnFailureAndRetainsProtocolHeadersOnAcceptedErrors() throws Exception {
        mvc.perform(get("/protocol/stale").accept("application/json;q=0, */*;q=1"))
                .andExpect(status().isNotAcceptable()).andExpect(content().string(""))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_ENCODING))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_LENGTH));
        mvc.perform(get("/protocol/limited"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string(HttpHeaders.RETRY_AFTER, "10"))
                .andExpect(jsonPath("$.code").value("VIREON.HTTP.429"));
        mvc.perform(head("/protocol/business").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isConflict()).andExpect(content().string(""));
    }

    @RestController
    public static class Controller {
        @GetMapping("/protocol/explicit")
        public ResponseEntity<VireonApiResponse<String>> explicit() {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(VireonResponses.ok("ready"));
        }

        @GetMapping("/protocol/vendor")
        public ResponseEntity<VireonApiResponse<String>> vendor() {
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.example+json"))
                    .body(VireonResponses.ok("ready"));
        }

        @GetMapping("/protocol/business")
        public void business() { throw new VireonException(VireonCommonErrorCode.CONFLICT); }

        @GetMapping("/protocol/binding")
        public VireonApiResponse<String> binding(@RequestHeader("X-Required") String required) {
            return VireonResponses.ok(required);
        }

        @PostMapping("/protocol/validation")
        public VireonApiResponse<Input> validation(@Valid @RequestBody Input input) { return VireonResponses.ok(input); }

        @GetMapping("/protocol/missing")
        public void missing() { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "private diagnostic"); }

        @GetMapping("/protocol/unknown")
        public void unknown() { throw new IllegalStateException("private diagnostic"); }

        @GetMapping("/protocol/stale")
        public void stale(HttpServletResponse response) {
            response.setContentType("text/plain");
            response.setContentLength(99);
            response.setHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
            throw new VireonException(VireonCommonErrorCode.CONFLICT);
        }

        @GetMapping("/protocol/limited")
        public void limited() {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS) {
                @Override
                public HttpHeaders getHeaders() {
                    HttpHeaders headers = new HttpHeaders();
                    headers.set(HttpHeaders.RETRY_AFTER, "10");
                    return headers;
                }
            };
        }
    }

    public record Input(@NotBlank String name) {}
}
