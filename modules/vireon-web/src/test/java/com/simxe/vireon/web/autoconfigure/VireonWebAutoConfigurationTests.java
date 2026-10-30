package com.simxe.vireon.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.simxe.vireon.web.error.VireonGlobalExceptionHandler;
import com.simxe.vireon.web.response.VireonResponseProtocolAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class VireonWebAutoConfigurationTests {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, VireonWebAutoConfiguration.class));

    @Test
    void configuresResponseAndExceptionContracts() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(VireonResponseProtocolAdvice.class)
                .doesNotHaveBean("vireonApiResponseBodyAdvice")
                .hasSingleBean(VireonGlobalExceptionHandler.class));
    }

    @Test
    void keepsConsumerOverrides() {
        VireonGlobalExceptionHandler handler = new VireonGlobalExceptionHandler();
        contextRunner.withBean(VireonGlobalExceptionHandler.class, () -> handler).run(context -> {
            assertThat(context).hasSingleBean(VireonGlobalExceptionHandler.class);
            assertThat(context.getBean(VireonGlobalExceptionHandler.class)).isSameAs(handler);
        });
    }
}
