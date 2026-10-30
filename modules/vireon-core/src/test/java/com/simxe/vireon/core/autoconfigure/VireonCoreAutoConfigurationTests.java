package com.simxe.vireon.core.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VireonCoreAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VireonCoreAutoConfiguration.class));

    @Test
    void configuresShanghaiClockByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ZoneId.class).hasSingleBean(Clock.class);
            assertThat(context.getBean(ZoneId.class)).isEqualTo(ZoneId.of("Asia/Shanghai"));
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        });
    }

    @Test
    void acceptsConfiguredTimeZone() {
        contextRunner.withPropertyValues("vireon.project.time-zone=UTC").run(context -> {
            assertThat(context.getBean(ZoneId.class)).isEqualTo(ZoneId.of("UTC"));
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("UTC"));
        });
    }

    @Test
    void keepsConsumerClockOverride() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneId.of("UTC"));
        contextRunner.withBean(Clock.class, () -> fixedClock).run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context.getBean(Clock.class)).isSameAs(fixedClock);
        });
    }

    @Test
    void rejectsInvalidTimeZone() {
        VireonProjectProperties properties = new VireonProjectProperties();

        assertThatIllegalArgumentException().isThrownBy(() -> properties.setTimeZone("not/a-zone"));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setTimeZone(" "));
    }
}
