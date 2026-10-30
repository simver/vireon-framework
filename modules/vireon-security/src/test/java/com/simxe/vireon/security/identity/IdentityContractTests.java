package com.simxe.vireon.security.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class IdentityContractTests {
    @Test
    void exposesOnlyIdentityFactsAndPermissionRequestWithoutSubject() {
        assertThatCode(() -> {
            Class<?> identity = Class.forName("com.simxe.vireon.security.identity.AuthenticatedIdentity");
            assertThat(Arrays.stream(identity.getRecordComponents()).map(component -> component.getName()))
                    .containsExactly("subjectId", "displayName", "sessionId", "applicationId", "expiresAt");
            Class<?> request = Class.forName("com.simxe.vireon.security.permission.PermissionRequest");
            assertThat(Arrays.stream(request.getRecordComponents()).map(component -> component.getName()))
                    .containsExactly("permissionCodes", "match");
        }).doesNotThrowAnyException();
    }
}
