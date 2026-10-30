package com.simxe.vireon.observability.autoconfigure;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public final class VireonObservabilityEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "vireonObservabilityDefaults";

    private static final Map<String, Object> DEFAULTS = Map.of(
            "management.defaults.metrics.export.enabled",
            "false",
            "management.tracing.enabled",
            "false",
            "management.otlp.tracing.export.enabled",
            "false");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, DEFAULTS));
        }
    }
}
