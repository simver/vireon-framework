package com.simxe.vireon.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import com.simxe.vireon.openapi.OpenApiRoutes;
import com.simxe.vireon.openapi.OpenApiSecurityAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;

/** 仅正式坐标和显式接入即可得到组件，不借用Framework源码或构建目录。 */
@SpringBootTest(classes = {VireonConsumerApplication.class, PublishedOpenApiTests.Security.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"vireon.openapi.enabled=true", "springdoc.api-docs.enabled=true", "vireon.openapi.path=/api/consumer/docs", "springdoc.api-docs.path=/api/consumer/v3/api-docs"})
class PublishedOpenApiTests {
    @Autowired TestRestTemplate http;
    @Autowired OpenApiRoutes routes;
    @Test void publishedAssetsCannotBypassTheDeclaredAuthority() {
        for (String path : routes.paths()) assertThat(http.getForEntity(path, byte[].class).getStatusCode().value()).as(path).isEqualTo(401);
        for (String path : List.of("/webjars/scalar/scalar.js", "/v3/api-docs", "/api/consumer/docs/")) {
            assertThat(http.getForEntity(path, byte[].class).getStatusCode().value()).isEqualTo(404);
        }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Security {
        @Bean OpenApiSecurityAdapter docsAuthority() {
            return new OpenApiSecurityAdapter("consumer:docs:read", request->com.simxe.vireon.security.permission.AccessDecision.DENY, (r,s,e) -> s.setStatus(401), (r,s,e) -> s.setStatus(403));
        }
    }
}
