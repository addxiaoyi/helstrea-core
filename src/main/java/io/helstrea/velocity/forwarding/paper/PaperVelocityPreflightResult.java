package io.helstrea.velocity.forwarding.paper;

import java.util.List;
import java.util.Objects;

public record PaperVelocityPreflightResult(List<PaperVelocityIssue> issues) {
    public PaperVelocityPreflightResult {
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public boolean valid() {
        return issues.isEmpty();
    }
}
