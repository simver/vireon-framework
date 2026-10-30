package com.simxe.vireon.security.permission;
import java.util.Set;
import java.util.Objects;
public record PermissionRequest(Set<String> permissionCodes, Match match) {
    public enum Match { ALL, ANY }
    public PermissionRequest {
        Objects.requireNonNull(match, "match");
        if (permissionCodes == null || permissionCodes.isEmpty() || permissionCodes.stream().anyMatch(code -> code == null || code.isBlank())) throw new IllegalArgumentException("Nonempty permission codes required");
        permissionCodes = Set.copyOf(permissionCodes);
    }
}
