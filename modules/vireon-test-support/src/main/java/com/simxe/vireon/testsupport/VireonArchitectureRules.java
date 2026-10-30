package com.simxe.vireon.testsupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import java.util.Arrays;

public final class VireonArchitectureRules {

    private VireonArchitectureRules() {}

    public static ArchRule noDependencies(String sourcePackage, String... forbiddenPackages) {
        requirePackagePattern(sourcePackage, "sourcePackage");
        if (forbiddenPackages == null || forbiddenPackages.length == 0) {
            throw new IllegalArgumentException("forbiddenPackages must not be empty");
        }
        Arrays.stream(forbiddenPackages)
                .forEach(pattern -> requirePackagePattern(pattern, "forbiddenPackages"));
        return noClasses()
                .that()
                .resideInAnyPackage(sourcePackage)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(forbiddenPackages);
    }

    private static void requirePackagePattern(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must contain non-blank package patterns");
        }
    }
}
