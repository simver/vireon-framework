package com.simxe.vireon.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simxe.vireon.web.api.VireonApiResponse;
import com.simxe.vireon.web.api.VireonResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.context.request.async.DeferredResult;

@SpringBootTest(classes = VireonWireContractTests.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "spring.main.banner-mode=off", "spring.aop.proxy-target-class=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VireonWireContractTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JdkApi jdkController;

    @Test
    void interfaceNamesAndJdkAopValidationSurviveRealServletDispatch() throws Exception {
        assertThat(AopUtils.isJdkDynamicProxy(jdkController)).isTrue();
        for (String path : List.of("/wire/interface-validation", "/wire/jdk-validation")) {
            var valid = send("GET", path + "?external-count=2", "application/json");
            assertThat(valid.statusCode()).isEqualTo(200);
            assertThat(new ObjectMapper().readTree(valid.body()).path("data").asInt()).isEqualTo(2);
            var response = send("GET", path + "?external-count=0", "application/json");
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
            var json = new ObjectMapper().readTree(response.body());
            assertThat(json.path("code").asText()).isEqualTo("VIREON.COMMON.VALIDATION_FAILED");
            assertThat(json.path("data").get(0).path("path").asText()).isEqualTo("external-count");
            assertThat(json.path("data").get(0).path("code").asText()).isEqualTo("VIREON.VALIDATION.MIN");
            response.headers().firstValueAsLong("Content-Length")
                    .ifPresent(length -> assertThat(length).isEqualTo(response.body().length));
        }
    }

    @Test
    void jdkValidationPreservesSafeReturnErrorsAndBodylessResponses() throws Exception {
        var response = send("GET", "/wire/jdk-return", "application/json");
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
        var json = new ObjectMapper().readTree(response.body());
        assertThat(json.path("data").isNull()).isTrue();
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).doesNotContain("private");
        var rejected = send("GET", "/wire/jdk-validation?external-count=0", "application/json;q=0, */*;q=1");
        assertThat(rejected.statusCode()).isEqualTo(406);
        assertThat(rejected.body()).isEmpty();
        assertThat(rejected.headers().firstValue("Content-Type")).isEmpty();
        assertThat(rejected.headers().firstValue("Accept")).hasValue("application/json");
        var head = send("HEAD", "/wire/jdk-validation?external-count=0", "text/plain");
        assertThat(head.statusCode()).isEqualTo(400);
        assertThat(head.body()).isEmpty();
    }

    @Test
    void containerLocationsSurviveRealServletDispatch() throws Exception {
        for (String path : List.of("/wire/people", "/wire/jdk-people", "/wire/people-map")) {
            boolean map = path.endsWith("-map");
            var response = send("POST", path, "application/json", map
                    ? "{\"alpha\":{\"name\":\"\"},\"beta\":{\"name\":\"\"}}"
                    : "[{\"name\":\"\"},{\"name\":\"\"}]");
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
            var json = new ObjectMapper().readTree(response.body());
            assertThat(json.path("code").asText()).isEqualTo("VIREON.COMMON.VALIDATION_FAILED");
            var paths = new java.util.ArrayList<String>();
            json.path("data").forEach(error -> {
                paths.add(error.path("path").asText());
                assertThat(error.path("code").asText()).isEqualTo("VIREON.VALIDATION.NOT_BLANK");
            });
            assertThat(paths).containsExactlyInAnyOrder(map
                    ? new String[] {"[alpha].name", "[beta].name"} : new String[] {"[0].name", "[1].name"});
            response.headers().firstValueAsLong("Content-Length")
                    .ifPresent(length -> assertThat(length).isEqualTo(response.body().length));
        }
    }

    @Test
    void setElementPathsSurviveNativeAndJdkAopServletDispatch() throws Exception {
        for (String path : List.of("/wire/people-set", "/wire/jdk-people-set")) {
            var response = send("POST", path, "application/json", "[{\"name\":\"\"}]");
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
            var json = new ObjectMapper().readTree(response.body());
            assertThat(json.path("data").get(0).path("path").asText()).isEqualTo("[].name");
            // Web 单模块没有 observability 过滤器，不凭空生成追踪上下文。
            assertThat(json.path("traceId").isNull()).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"direct", "entity", "callable", "deferred"})
    void streamApplicationFailuresRespectActualCommitState(String shape) throws Exception {
        var response = send("GET", "/wire/stream-failure/" + shape + "?committed=false", "application/json");
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
        var json = new ObjectMapper().readTree(response.body());
        assertThat(json.path("code").asText()).isEqualTo("VIREON.COMMON.INTERNAL_ERROR");
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).doesNotContain("partial", "private");
        assertThat(json.path("traceId").isNull()).isTrue();
        response.headers().firstValueAsLong("Content-Length")
                .ifPresent(length -> assertThat(length).isEqualTo(response.body().length));

        var rejected = send("GET", "/wire/stream-failure/" + shape + "?committed=false", "text/plain");
        assertThat(rejected.statusCode()).isEqualTo(406);
        assertThat(rejected.body()).isEmpty();
        assertThat(rejected.headers().firstValue("Content-Type")).isEmpty();
        assertThat(rejected.headers().firstValue("Accept")).hasValue("application/json");

        var committed = send("GET", "/wire/stream-failure/" + shape + "?committed=true", "*/*");
        assertThat(committed.statusCode()).isEqualTo(200);
        assertThat(committed.headers().firstValue("Content-Type")).hasValue("application/octet-stream");
        assertThat(committed.body()).isEqualTo("partial".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void preservesExactTextBytesContentTypeAndLength() throws Exception {
        var response = send("GET", "/wire/text", "*/*");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("text/plain;charset=UTF-8");
        assertThat(response.headers().firstValueAsLong("Content-Length")).hasValue(6);
        assertThat(response.body()).isEqualTo("准备".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void directNestedContainerPathsSurviveRealServletDispatch() throws Exception {
        for (String path : List.of("/wire/matrix", "/wire/jdk-matrix", "/wire/map-list")) {
            boolean map = path.endsWith("map-list");
            var response = send("POST", path, "application/json", map
                    ? "{\"alpha\":[{\"name\":\"\"}],\"beta\":[{\"name\":\"\"}]}"
                    : "[[{\"name\":\"\"}],[{\"name\":\"\"}]]");
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
            var json = new ObjectMapper().readTree(response.body());
            var paths = new java.util.ArrayList<String>();
            json.path("data").forEach(error -> {
                paths.add(error.path("path").asText());
                assertThat(error.path("code").asText()).isEqualTo("VIREON.VALIDATION.NOT_BLANK");
            });
            assertThat(paths).containsExactlyInAnyOrder(map ? new String[] {"[alpha][0].name", "[beta][0].name"}
                    : new String[] {"[0][0].name", "[1][0].name"});
            response.headers().firstValueAsLong("Content-Length")
                    .ifPresent(length -> assertThat(length).isEqualTo(response.body().length));
        }
    }

    @Test
    void writesExplicitEnvelopeWithCorrectDeclaredLengthAndNoSecondWrapping() throws Exception {
        var response = send("GET", "/wire/json", "application/json");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
        assertThat(response.headers().firstValueAsLong("Content-Length")).hasValue(response.body().length);
        var json = new ObjectMapper().readTree(response.body());
        assertThat(json.path("data").asText()).isEqualTo("准备");
        assertThat(json.path("code").asText()).isEqualTo(VireonApiResponse.SUCCESS_CODE);
    }

    @Test
    void rejectsJsonWithoutLeakingPreviousRepresentationHeaders() throws Exception {
        var response = send("GET", "/wire/json", "application/json;q=0, */*;q=1");
        assertThat(response.statusCode()).isEqualTo(406);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers().firstValue("Content-Type")).isEmpty();
        assertThat(response.headers().firstValue("Accept")).hasValue("application/json");
        assertThat(response.headers().firstValueAsLong("Content-Length").orElse(0)).isZero();
    }

    @Test
    void bodylessStatusesAndHeadHaveNoWireBytes() throws Exception {
        for (int status : List.of(204, 205, 304)) {
            var response = send("GET", "/wire/bodyless?status=" + status, "*/*");
            assertThat(response.statusCode()).isEqualTo(status);
            assertThat(response.body()).isEmpty();
        }
        var head = send("HEAD", "/wire/json", "application/json");
        assertThat(head.statusCode()).isEqualTo(200);
        assertThat(head.body()).isEmpty();
    }

    @Test
    void fileAndStreamingResponsesKeepTheirBytes() throws Exception {
        for (String path : List.of("/wire/file", "/wire/stream")) {
            var response = send("GET", path, "*/*");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("raw-bytes".getBytes(StandardCharsets.UTF_8));
            assertThat(response.headers().firstValue("Content-Type")).hasValue("application/octet-stream");
        }
    }

    @Test
    void methodValidationKeepsDetailsOnTheWire() throws Exception {
        var response = send("GET", "/wire/validation?count=0", "application/json");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
        var json = new ObjectMapper().readTree(response.body());
        assertThat(json.path("data").get(0).path("path").asText()).isEqualTo("count");
        assertThat(json.path("data").get(0).path("code").asText()).isEqualTo("VIREON.VALIDATION.MIN");
    }

    @Test
    void aopReturnFailureKeepsSafeServerStatusAndNegotiationOnTheWire() throws Exception {
        var response = send("GET", "/wire/invalid-service-return", "application/json");
        assertThat(response.statusCode()).isEqualTo(500);
        var json = new ObjectMapper().readTree(response.body());
        assertThat(json.path("code").asText()).isEqualTo("VIREON.COMMON.INTERNAL_ERROR");
        assertThat(json.path("data").isNull()).isTrue();
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).doesNotContain("private");
        var rejected = send("GET", "/wire/invalid-service-return", "application/json;q=0, */*;q=1");
        assertThat(rejected.statusCode()).isEqualTo(406);
        assertThat(rejected.body()).isEmpty();
        assertThat(rejected.headers().firstValue("Content-Type")).isEmpty();
    }

    private HttpResponse<byte[]> send(String method, String path, String accept) throws Exception {
        return send(method, path, accept, null);
    }

    private HttpResponse<byte[]> send(String method, String path, String accept, String body) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10)).header("Accept", accept)
                    .header("Content-Type", "application/json")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({Controller.class, CheckedService.class, InterfaceController.class, JdkController.class})
    static class Application {}

    public interface NativeApi {
        @PostMapping("/wire/people-set")
        VireonApiResponse<Set<Person>> peopleSet(@RequestBody @Size(min = 1) @Valid Set<Person> people);

        @PostMapping("/wire/matrix")
        VireonApiResponse<List<List<Person>>> matrix(@RequestBody @Size(min = 1) List<@Valid List<@Valid Person>> rows);

        @PostMapping("/wire/map-list")
        VireonApiResponse<Map<String, List<Person>>> mapList(@RequestBody @Size(min = 1) Map<String, @Valid List<@Valid Person>> rows);

        @PostMapping("/wire/people")
        VireonApiResponse<List<Person>> people(@RequestBody @Size(min = 1) @Valid List<Person> people);

        @PostMapping("/wire/people-map")
        VireonApiResponse<Map<String, Person>> peopleMap(@RequestBody @Size(min = 1) @Valid Map<String, Person> people);

        @GetMapping("/wire/interface-validation")
        VireonApiResponse<Integer> count(@RequestParam("external-count") @Min(1) int value);
    }

    @RestController
    public static class InterfaceController implements NativeApi {
        public VireonApiResponse<Set<Person>> peopleSet(Set<Person> people) { return VireonResponses.ok(people); }
        public VireonApiResponse<List<List<Person>>> matrix(List<List<Person>> rows) { return VireonResponses.ok(rows); }
        public VireonApiResponse<Map<String, List<Person>>> mapList(Map<String, List<Person>> rows) { return VireonResponses.ok(rows); }
        public VireonApiResponse<List<Person>> people(List<Person> people) { return VireonResponses.ok(people); }
        public VireonApiResponse<Map<String, Person>> peopleMap(Map<String, Person> people) { return VireonResponses.ok(people); }
        public VireonApiResponse<Integer> count(int count) { return VireonResponses.ok(count); }
    }

    @RestController
    @Validated
    public interface JdkApi {
        @PostMapping("/wire/jdk-people-set")
        VireonApiResponse<Set<Person>> peopleSet(@RequestBody @Size(min = 1) @Valid Set<Person> people);

        @PostMapping("/wire/jdk-matrix")
        VireonApiResponse<List<List<Person>>> matrix(@RequestBody @Size(min = 1) List<@Valid List<@Valid Person>> rows);

        @PostMapping("/wire/jdk-people")
        VireonApiResponse<List<Person>> people(@RequestBody @Size(min = 1) @Valid List<Person> people);

        @GetMapping("/wire/jdk-validation")
        VireonApiResponse<Integer> count(@RequestParam("external-count") @Min(1) int value);

        @GetMapping("/wire/jdk-return")
        @NotBlank(message = "private JDK return")
        String invalidReturn();
    }

    @RestController
    @Validated
    public static class JdkController implements JdkApi {
        public VireonApiResponse<Set<Person>> peopleSet(Set<Person> people) { return VireonResponses.ok(people); }
        public VireonApiResponse<List<List<Person>>> matrix(List<List<Person>> rows) { return VireonResponses.ok(rows); }
        public VireonApiResponse<List<Person>> people(List<Person> people) { return VireonResponses.ok(people); }
        public VireonApiResponse<Integer> count(int count) { return VireonResponses.ok(count); }
        public String invalidReturn() { return ""; }
    }

    @RestController
    static class Controller {
        private final CheckedService service;

        Controller(CheckedService service) { this.service = service; }

        @GetMapping("/wire/stream-failure/direct")
        StreamingResponseBody directFailure(@RequestParam boolean committed, HttpServletResponse response) {
            response.setContentType("application/octet-stream");
            return failingBody(committed);
        }

        @GetMapping("/wire/stream-failure/entity")
        ResponseEntity<StreamingResponseBody> entityFailure(@RequestParam boolean committed) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(failingBody(committed));
        }

        @GetMapping("/wire/stream-failure/callable")
        Callable<StreamingResponseBody> callableFailure(@RequestParam boolean committed, HttpServletResponse response) {
            response.setContentType("application/octet-stream");
            return () -> failingBody(committed);
        }

        @GetMapping("/wire/stream-failure/deferred")
        DeferredResult<StreamingResponseBody> deferredFailure(@RequestParam boolean committed, HttpServletResponse response) {
            response.setContentType("application/octet-stream");
            var result = new DeferredResult<StreamingResponseBody>();
            result.setResult(failingBody(committed));
            return result;
        }

        private static StreamingResponseBody failingBody(boolean committed) {
            return output -> {
                output.write("partial".getBytes(StandardCharsets.UTF_8));
                if (committed) {
                    output.flush();
                }
                throw new IOException("private application I/O failure");
            };
        }

        @GetMapping("/wire/validation")
        VireonApiResponse<Integer> validation(@RequestParam @Min(1) int count) {
            return VireonResponses.ok(count);
        }

        @GetMapping("/wire/invalid-service-return")
        VireonApiResponse<String> invalidServiceReturn() { return VireonResponses.ok(service.value()); }

        @GetMapping("/wire/text")
        ResponseEntity<String> text() {
            return ResponseEntity.ok().contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .contentLength(6).body("准备");
        }

        @GetMapping("/wire/json")
        ResponseEntity<VireonApiResponse<String>> json() throws Exception {
            var body = VireonResponses.ok("准备");
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .contentLength(new ObjectMapper().writeValueAsBytes(body).length).body(body);
        }

        @GetMapping("/wire/bodyless")
        ResponseEntity<Map<String, String>> bodyless(@RequestParam int status) {
            return ResponseEntity.status(status).body(Map.of("must", "not be sent"));
        }

        @GetMapping("/wire/file")
        ResponseEntity<ByteArrayResource> file() {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new ByteArrayResource("raw-bytes".getBytes(StandardCharsets.UTF_8)));
        }

        @GetMapping("/wire/stream")
        ResponseEntity<StreamingResponseBody> stream() {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(output -> output.write("raw-bytes".getBytes(StandardCharsets.UTF_8)));
        }
    }

    public record Person(@NotBlank String name) {}

    @Validated
    public static class CheckedService {
        @NotBlank(message = "private service return")
        public String value() { return ""; }
    }
}
