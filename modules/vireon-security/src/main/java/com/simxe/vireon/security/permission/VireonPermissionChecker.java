package com.simxe.vireon.security.permission;
import com.simxe.vireon.core.error.VireonErrorCode;
import com.simxe.vireon.core.error.VireonException;
import com.simxe.vireon.security.identity.CurrentIdentity;
import java.time.Clock;
import java.util.Objects;
public final class VireonPermissionChecker {
    private final CurrentIdentity identity;
    private final PermissionEvaluator evaluator;
    private final Clock clock;
    public VireonPermissionChecker(CurrentIdentity identity, PermissionEvaluator evaluator) { this(identity, evaluator, Clock.systemUTC()); }
    public VireonPermissionChecker(CurrentIdentity identity, PermissionEvaluator evaluator, Clock clock) {
        this.identity = Objects.requireNonNull(identity); this.evaluator = Objects.requireNonNull(evaluator); this.clock = Objects.requireNonNull(clock);
    }
    public AccessDecision evaluate(PermissionRequest request) {
        Objects.requireNonNull(request);
        var current = Objects.requireNonNull(identity.current());
        if (current.isEmpty() || !current.get().expiresAt().isAfter(clock.instant())) return AccessDecision.DENY;
        return Objects.requireNonNull(evaluator.evaluate(request), "Evaluator returned null");
    }
    public void require(PermissionRequest request, VireonErrorCode denied) { if (evaluate(request) != AccessDecision.ALLOW) throw new VireonException(denied); }
}
