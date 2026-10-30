package com.simxe.vireon.openapi;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** 在springdoc自动配置前提供最低优先级的关闭默认值。 */
public final class OpenApiEnvironmentDefaults implements EnvironmentPostProcessor, Ordered {
    @Override public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(new MapPropertySource("vireonOpenApiDefaults", Map.of(
                "springdoc.api-docs.enabled", false, "springdoc.swagger-ui.enabled", false, "scalar.enabled", false)));
    }
    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
