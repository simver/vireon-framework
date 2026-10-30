package com.simxe.vireon.core.autoconfigure;

import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "vireon.project")
public class VireonProjectProperties {

    public static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    private String timeZone = DEFAULT_TIME_ZONE;

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        if (!StringUtils.hasText(timeZone)) {
            throw new IllegalArgumentException("timeZone must not be blank");
        }
        this.timeZone = normalizeZoneId(timeZone);
    }

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    private static String normalizeZoneId(String value) {
        String candidate = value.trim();
        try {
            return ZoneId.of(candidate).getId();
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("timeZone must be a valid IANA zone id", exception);
        }
    }
}
