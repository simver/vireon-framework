package com.simxe.vireon.consumer;

import com.simxe.vireon.observability.tracing.VireonTraceIdContext;
import com.simxe.vireon.web.api.VireonApiResponse;
import com.simxe.vireon.web.api.VireonResponses;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@SpringBootApplication
public class VireonConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(VireonConsumerApplication.class, args);
    }

    // 新控制器使用 MVC 原生校验；对外参数名属于接口契约，不随实现变量重命名变化。
    public interface ValidationApi {
        @PostMapping("/set-smoke")
        VireonApiResponse<Set<Person>> peopleSet(@RequestBody @Size(min = 1) @Valid Set<Person> people);

        @PostMapping("/matrix-smoke")
        VireonApiResponse<List<List<Person>>> matrix(@RequestBody @Size(min = 1) List<@Valid List<@Valid Person>> rows);

        @PostMapping("/people-smoke")
        VireonApiResponse<List<Person>> people(@RequestBody @Size(min = 1) @Valid List<Person> people);

        @GetMapping("/validation-smoke")
        VireonApiResponse<Integer> count(@RequestParam("external-count") @Min(1) int value);
    }

    @RestController
    public static class ValidationController implements ValidationApi {
        public VireonApiResponse<Set<Person>> peopleSet(Set<Person> people) { return VireonResponses.ok(people); }
        public VireonApiResponse<List<List<Person>>> matrix(List<List<Person>> rows) { return VireonResponses.ok(rows); }
        public VireonApiResponse<List<Person>> people(List<Person> people) { return VireonResponses.ok(people); }
        public VireonApiResponse<Integer> count(int count) { return VireonResponses.ok(count); }
    }

    // 仅示范已有 AOP 消费者的兼容路径；不作为新控制器的默认模板。
    @RestController
    @Validated
    public interface AopValidationApi {
        @PostMapping("/aop-set-smoke")
        VireonApiResponse<Set<Person>> peopleSet(@RequestBody @Size(min = 1) @Valid Set<Person> people);

        @PostMapping("/aop-matrix-smoke")
        VireonApiResponse<List<List<Person>>> matrix(@RequestBody @Size(min = 1) List<@Valid List<@Valid Person>> rows);

        @PostMapping("/aop-people-smoke")
        VireonApiResponse<List<Person>> people(@RequestBody @Size(min = 1) @Valid List<Person> people);

        @GetMapping("/aop-validation-smoke")
        VireonApiResponse<Integer> count(@RequestParam(name = "external-count") @Min(1) int value);
    }

    @RestController
    @Validated
    public static class AopValidationController implements AopValidationApi {
        public VireonApiResponse<Set<Person>> peopleSet(Set<Person> people) { return VireonResponses.ok(people); }
        public VireonApiResponse<List<List<Person>>> matrix(List<List<Person>> rows) { return VireonResponses.ok(rows); }
        public VireonApiResponse<List<Person>> people(List<Person> people) { return VireonResponses.ok(people); }
        public VireonApiResponse<Integer> count(int count) { return VireonResponses.ok(count); }
    }

    public record Person(@NotBlank String name) {}

    @RestController
    static class SmokeController {

        // 特殊表示不选入业务成功返回类型断言；仍验证其异常经过发布后的统一出口。
        @GetMapping("/stream-failure-smoke")
        StreamingResponseBody streamFailure() {
            return output -> {
                output.write(1);
                throw new IOException("private stream diagnostic");
            };
        }

        @GetMapping("/callable-stream-failure-smoke")
        Callable<StreamingResponseBody> callableStreamFailure() { return this::streamFailure; }

        @GetMapping("/smoke")
        VireonApiResponse<Map<String, String>> smoke() {
            return VireonResponses.ok(Map.of("status", "ready"));
        }

        @GetMapping("/async-smoke")
        Callable<VireonApiResponse<Map<String, String>>> asyncSmoke() {
            return () -> VireonResponses.ok(Map.of(
                    "traceId", VireonTraceIdContext.currentTraceId().orElse("missing")));
        }
    }
}
