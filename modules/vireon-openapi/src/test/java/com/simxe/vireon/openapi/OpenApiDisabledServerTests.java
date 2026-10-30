package com.simxe.vireon.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(classes = OpenApiServerTests.Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiDisabledServerTests {
    @Autowired TestRestTemplate http;
    @Test void noConfigurationClosesSchemaAndDependencyAssets() {
        for (String path : java.util.List.of("/docs", "/docs/scalar.js", "/v3/api-docs", "/v3/api-docs.yaml", "/webjars/scalar/scalar.js")) {
            assertThat(http.getForEntity(path, String.class).getStatusCode().value()).as(path).isEqualTo(404);
        }
    }
}
