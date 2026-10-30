package com.simxe.vireon.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.simxe.vireon.observability.tracing.VireonTraceIdFilter;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.env.MockEnvironment;

class VireonObservabilityAutoConfigurationTests {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VireonObservabilityAutoConfiguration.class));

    @Test
    void configuresAuthoritativeTraceFilter() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("vireonTraceIdFilterRegistration");
            FilterRegistrationBean<?> registration =
                    context.getBean("vireonTraceIdFilterRegistration", FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isInstanceOf(VireonTraceIdFilter.class);
            assertThat(registration.isAsyncSupported()).isTrue();
            assertThat(registration.determineDispatcherTypes())
                    .containsExactlyInAnyOrder(
                            DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        });
    }

    @Test
    void keepsExternalTelemetryDisabledByDefaultWithoutOverridingUserValues() {
        MockEnvironment environment = new MockEnvironment();
        VireonObservabilityEnvironmentPostProcessor processor =
                new VireonObservabilityEnvironmentPostProcessor();

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.defaults.metrics.export.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("management.tracing.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("management.otlp.tracing.export.enabled")).isEqualTo("false");
    }

    @Test
    void allowsExplicitTelemetryConfigurationToOverrideFrameworkDefaults() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("management.defaults.metrics.export.enabled", "true")
                .withProperty("management.otlp.metrics.export.enabled", "true")
                .withProperty("management.tracing.enabled", "true");
        VireonObservabilityEnvironmentPostProcessor processor =
                new VireonObservabilityEnvironmentPostProcessor();

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.defaults.metrics.export.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("management.tracing.enabled")).isEqualTo("true");
    }
}
