package io.helstrea.velocity.forwarding.paper;

import java.util.List;
import java.util.Objects;

public final class PaperVelocityPreflightException extends Exception {
    private static final long serialVersionUID = 1L;

    private final PaperVelocityIssue[] issues;

    public PaperVelocityPreflightException(List<PaperVelocityIssue> issues) {
        super("Paper Velocity configuration is invalid: " + issues);
        List<PaperVelocityIssue> copy =
                List.copyOf(Objects.requireNonNull(issues, "issues"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("issues must not be empty");
        }
        this.issues = copy.toArray(PaperVelocityIssue[]::new);
    }

    public List<PaperVelocityIssue> issues() {
        return List.of(issues.clone());
    }
}
