package com.simxe.vireon.security.identity;
import java.time.Instant;
import java.util.Objects;
public record AuthenticatedIdentity(String subjectId, String displayName, String sessionId, String applicationId, Instant expiresAt) {
    public AuthenticatedIdentity {
        if (subjectId == null || subjectId.isBlank() || sessionId == null || sessionId.isBlank() || applicationId == null || applicationId.isBlank()) throw new IllegalArgumentException("Identity references are required");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
