package io.helstrea.velocity.forwarding.paper;

import java.util.List;

public final class PaperVelocityPreflightSelfTest {
    private PaperVelocityPreflightSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        acceptsValidConfiguration();
        reportsAllBlockingIssues();
        suppressesSecretMismatchWhenSecretIsMissing();
        failsClosedAtStartup();
        System.out.println("Paper Velocity preflight self-test: PASS");
    }

    private static void acceptsValidConfiguration() throws Exception {
        PaperVelocitySettings settings = validSettings();
        PaperVelocityPreflightResult result = PaperVelocityPreflight.inspect(settings);
        check(result.valid(), "valid Paper Velocity configuration must pass");
        PaperVelocityPreflight.requireValid(settings);
    }

    private static void reportsAllBlockingIssues() {
        PaperVelocityPreflightResult result = PaperVelocityPreflight.inspect(invalidSettings());
        List<PaperVelocityIssue> expected = expectedIssues();
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

    private static void failsClosedAtStartup() throws Exception {
        try {
            PaperVelocityPreflight.requireValid(invalidSettings());
            throw new AssertionError("invalid configuration must fail startup");
        } catch (PaperVelocityPreflightException exception) {
            check(
                    exception.issues().equals(expectedIssues()),
                    "startup failure must preserve all preflight issues"
            );
        }
    }

    private static PaperVelocitySettings validSettings() {
        return new PaperVelocitySettings(
                false,
                false,
                true,
                true,
                true,
                true,
                true
        );
    }

    private static PaperVelocitySettings invalidSettings() {
        return new PaperVelocitySettings(
                true,
                true,
                false,
                false,
                true,
                true,
                false
        );
    }

    private static List<PaperVelocityIssue> expectedIssues() {
        return List.of(
                PaperVelocityIssue.SERVER_ONLINE_MODE_ENABLED,
                PaperVelocityIssue.BUNGEECORD_FORWARDING_ENABLED,
                PaperVelocityIssue.VELOCITY_FORWARDING_DISABLED,
                PaperVelocityIssue.FORWARDING_SECRET_MISMATCH,
                PaperVelocityIssue.PROXY_ONLINE_MODE_MISMATCH
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
