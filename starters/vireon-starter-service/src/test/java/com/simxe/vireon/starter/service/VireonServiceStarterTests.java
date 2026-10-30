package com.simxe.vireon.starter.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.simxe.vireon.core.autoconfigure.VireonCoreAutoConfiguration;
import com.simxe.vireon.observability.autoconfigure.VireonObservabilityAutoConfiguration;
import com.simxe.vireon.security.autoconfigure.VireonSecurityAutoConfiguration;
import com.simxe.vireon.security.permission.VireonPermissionChecker;
import com.simxe.vireon.security.principal.VireonAuthenticationContext;
import com.simxe.vireon.web.autoconfigure.VireonWebAutoConfiguration;
import com.simxe.vireon.web.error.VireonGlobalExceptionHandler;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class VireonServiceStarterTests {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    VireonCoreAutoConfiguration.class,
                    VireonWebAutoConfiguration.class,
                    VireonSecurityAutoConfiguration.class,
                    VireonObservabilityAutoConfiguration.class));

    @Test
    void composesMinimalServiceCapabilitiesWithoutInventingAuthentication() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(Clock.class)
                .hasSingleBean(VireonGlobalExceptionHandler.class)
                .hasSingleBean(VireonPermissionChecker.class)
                .hasBean("vireonTraceIdFilterRegistration")
                .doesNotHaveBean(VireonAuthenticationContext.class)
                .doesNotHaveBean("securityFilterChain"));
    }
}
