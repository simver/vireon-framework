package com.simxe.vireon.testsupport;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public final class VireonApplicationContextRunner {

    private VireonApplicationContextRunner() {}

    public static ApplicationContextRunner withAutoConfigurations(Class<?>... autoConfigurations) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(autoConfigurations));
    }
}
