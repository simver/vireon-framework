package com.simxe.vireon.core.autoconfigure;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(VireonProjectProperties.class)
public class VireonCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ZoneId vireonProjectZoneId(VireonProjectProperties properties) {
        return properties.zoneId();
    }

    @Bean
    @ConditionalOnMissingBean
    Clock vireonProjectClock(ZoneId vireonProjectZoneId) {
        return Clock.system(vireonProjectZoneId);
    }
}
