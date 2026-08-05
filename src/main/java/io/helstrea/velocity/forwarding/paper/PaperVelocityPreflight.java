package io.helstrea.velocity.forwarding.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PaperVelocityPreflight {
    private PaperVelocityPreflight() {
    }

    public static PaperVelocityPreflightResult inspect(PaperVelocitySettings settings) {
        Objects.requireNonNull(settings, "settings");
        List<PaperVelocityIssue> issues = new ArrayList<>();
        if (settings.serverOnlineMode()) {
            issues.add(PaperVelocityIssue.SERVER_ONLINE_MODE_ENABLED);
        }
        if (settings.bungeeCordForwardingEnabled()) {
            issues.add(PaperVelocityIssue.BUNGEECORD_FORWARDING_ENABLED);
        }
        if (!settings.velocityForwardingEnabled()) {
            issues.add(PaperVelocityIssue.VELOCITY_FORWARDING_DISABLED);
        }
        if (!settings.forwardingSecretPresent()) {
            issues.add(PaperVelocityIssue.FORWARDING_SECRET_MISSING);
        } else if (!settings.forwardingSecretMatches()) {
            issues.add(PaperVelocityIssue.FORWARDING_SECRET_MISMATCH);
        }
        if (settings.paperVelocityOnlineMode() != settings.proxyOnlineMode()) {
            issues.add(PaperVelocityIssue.PROXY_ONLINE_MODE_MISMATCH);
        }
        return new PaperVelocityPreflightResult(issues);
    }

    public static void requireValid(PaperVelocitySettings settings)
            throws PaperVelocityPreflightException {
        PaperVelocityPreflightResult result = inspect(settings);
        if (!result.valid()) {
            throw new PaperVelocityPreflightException(result.issues());
        }
    }
}
