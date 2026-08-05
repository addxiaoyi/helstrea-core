package io.helstrea.velocity.forwarding.paper;

import java.util.List;
import java.util.Objects;

public final class PaperVelocityPreflightException extends Exception {
    private static final long serialVersionUID = 1L;

    private final List<PaperVelocityIssue> issues;

    public PaperVelocityPreflightException(List<PaperVelocityIssue> issues) {
        super("Paper Velocity configuration is invalid: " + issues);
        this.issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        if (this.issues.isEmpty()) {
            throw new IllegalArgumentException("issues must not be empty");
        }
    }

    public List<PaperVelocityIssue> issues() {
        return issues;
    }
}
