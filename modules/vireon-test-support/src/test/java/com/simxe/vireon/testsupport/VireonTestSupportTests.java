package com.simxe.vireon.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

class VireonTestSupportTests {

    @Test
    void createsContextRunnerForSelectedAutoConfiguration() {
        VireonApplicationContextRunner.withAutoConfigurations(SampleAutoConfiguration.class)
                .run(context -> assertThat(context).hasBean("sampleValue"));
    }

    @Test
    void rejectsEmptyArchitectureBoundary() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> VireonArchitectureRules.noDependencies("com.simxe.vireon.."));
    }

    @AutoConfiguration
    static class SampleAutoConfiguration {

        @Bean
        String sampleValue() {
            return "ready";
        }
    }
}
