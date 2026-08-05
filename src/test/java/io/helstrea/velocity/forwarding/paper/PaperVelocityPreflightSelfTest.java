package io.helstrea.velocity.forwarding.paper;

import java.util.List;

public final class PaperVelocityPreflightSelfTest {
    private PaperVelocityPreflightSelfTest() {
    }

    public static void main(String[] args) {
        acceptsValidConfiguration();
        reportsAllBlockingIssues();
        suppressesSecretMismatchWhenSecretIsMissing();
        System.out.println("Paper Velocity preflight self-test: PASS");
    }

    private static void acceptsValidConfiguration() {
        PaperVelocityPreflightResult result = PaperVelocityPreflight.inspect(
                new PaperVelocitySettings(
                        false,
                        false,
                        true,
                        true,
                        true,
                        true,
                        true
                )
        );
        check(result.valid(), "valid Paper Velocity configuration must pass");
    }

    private static void reportsAllBlockingIssues() {
        PaperVelocityPreflightResult result = PaperVelocityPreflight.inspect(
                new PaperVelocitySettings(
                        true,
                        true,
                        false,
                        false,
                        true,
                        true,
                        false
                )
        );
        List<PaperVelocityIssue> expected = List.of(
                PaperVelocityIssue.SERVER_ONLINE_MODE_ENABLED,
                PaperVelocityIssue.BUNGEECORD_FORWARDING_ENABLED,
                PaperVelocityIssue.VELOCITY_FORWARDING_DISABLED,
                PaperVelocityIssue.FORWARDING_SECRET_MISMATCH,
                PaperVelocityIssue.PROXY_ONLINE_MODE_MISMATCH
        );
        check(expected.equals(result.issues()), "all blocking issues must be reported");
        check(!result.valid(), "invalid configuration must fail preflight");
    }

    private static void suppressesSecretMismatchWhenSecretIsMissing() {
        PaperVelocityPreflightResult result = PaperVelocityPreflight.inspect(
                new PaperVelocitySettings(
                        false,
                        false,
                        true,
                        true,
                        true,
                        false,
                        false
                )
        );
        check(
                result.issues().equals(List.of(PaperVelocityIssue.FORWARDING_SECRET_MISSING)),
                "missing secret must not also report a mismatch"
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
