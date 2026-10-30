package com.simxe.vireon.security.permission;
@FunctionalInterface
public interface PermissionEvaluator { AccessDecision evaluate(PermissionRequest request); }
