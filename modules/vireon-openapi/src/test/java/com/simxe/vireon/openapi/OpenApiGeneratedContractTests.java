package com.simxe.vireon.openapi;

import static org.assertj.core.api.Assertions.*;
import com.simxe.vireon.openapi.build.ApiReviewGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiGeneratedContractTests {
    @TempDir Path directory;
    @Test void buildCatalogsMustLoadUnderRuntimeProtocol() throws Exception {
        Path source = Files.createDirectory(directory.resolve("source"));
        Path migrations = Files.createDirectory(directory.resolve("migrations"));
        Path output = directory.resolve("classes");
        Path notes = directory.resolve("notes.json");
        Files.writeString(source.resolve("Example.java"), """
                package example;
                @RestController
                class Example {
                    @Hidden @Operation(operationId="internalRead", summary="内部只读接口")
                    @GetMapping("/internal/example")
                    public String read() { return "ok"; }
                }
                """);
        Files.writeString(migrations.resolve("V1__example.sql"), """
                CREATE TABLE example.record (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, label varchar(100) NOT NULL);
                COMMENT ON TABLE example.record IS '样例';
                CREATE INDEX record_label ON example.record (label DESC, id);
                """);
        Files.writeString(notes, """
                {"relationships":[],"permissions":[{"code":"example:docs:read","description":"评审"}],"runtimeNotes":[{"title":"文本安全","description":"<script>alert(1)</script>"}]}
                """);
        ApiReviewGenerator.generate("example", source, migrations, notes, output, false, false);
        var review = OpenApiMetadata.parse(Files.readAllBytes(output.resolve("META-INF/vireon/api-review.json")), false);
        var history = OpenApiMetadata.parse(Files.readAllBytes(output.resolve("META-INF/vireon/api-git-history.json")), true);
        assertThat(review.get("internalEndpoints").get(0).get("operationId").asText()).isEqualTo("internalRead");
        assertThat(history.get("operations").get(0).get("state").asText()).isEqualTo("unavailable");
        ApiReviewGenerator.generate("example", source, migrations, notes, output, false, true);
        Files.writeString(source.resolve("Example.java"), Files.readString(source.resolve("Example.java")).replace("return \"ok\"", "return \"changed\""));
        assertThatThrownBy(() -> ApiReviewGenerator.generate("example", source, migrations, notes, output, false, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
