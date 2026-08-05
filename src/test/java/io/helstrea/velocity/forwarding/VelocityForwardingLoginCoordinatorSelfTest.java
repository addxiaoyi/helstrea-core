package io.helstrea.velocity.forwarding;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class VelocityForwardingLoginCoordinatorSelfTest {
    private static final byte[] SECRET =
            "correct horse battery staple".getBytes(StandardCharsets.UTF_8);

    private VelocityForwardingLoginCoordinatorSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        appliesVerifiedIdentityOnce();
        doesNotApplyUnverifiedIdentity();
        propagatesIdentityApplicationFailure();
        System.out.println("Velocity login coordinator self-test: PASS");
    }

    private static void appliesVerifiedIdentityOnce() throws Exception {
        VelocityForwardingCodec codec = codec();
        AtomicReference<ForwardedPlayer> applied = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        VelocityForwardingLoginCoordinator<Object> coordinator = coordinator(
                (connection, player) -> {
                    calls.incrementAndGet();
                    applied.set(player);
                }
        );
        ForwardedPlayer expected = player();
        ForwardedPlayer actual = coordinator.acceptResponse(
                new Object(),
                73,
                codec.encodeV1(expected)
        );
        check(expected.equals(actual), "coordinator must return the verified player");
        check(expected.equals(applied.get()), "verified identity must be applied");
        check(calls.get() == 1, "verified identity must be applied exactly once");
        check(coordinator.consumed(), "successful login must consume the session");
    }

    private static void doesNotApplyUnverifiedIdentity() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        VelocityForwardingLoginCoordinator<Object> coordinator = coordinator(
                (connection, player) -> calls.incrementAndGet()
        );
        byte[] invalid = codec().encodeV1(player());
        invalid[invalid.length - 1] ^= 1;
        expect(
                ForwardingError.INVALID_SIGNATURE,
                () -> coordinator.acceptResponse(new Object(), 73, invalid)
        );
        check(calls.get() == 0, "unverified identity must never be applied");
        check(coordinator.consumed(), "matched invalid response must consume the session");
    }

    private static void propagatesIdentityApplicationFailure() throws Exception {
        VelocityForwardingLoginCoordinator<Object> coordinator = coordinator(
                (connection, player) -> {
                    throw new VelocityForwardingException(
                            ForwardingError.IDENTITY_APPLICATION_FAILED,
                            "Unable to replace the backend login identity"
                    );
                }
        );
        expect(
                ForwardingError.IDENTITY_APPLICATION_FAILED,
                () -> coordinator.acceptResponse(
                        new Object(),
                        73,
                        codec().encodeV1(player())
                )
        );
        check(coordinator.consumed(), "application failure must not reopen the session");
    }

    private static VelocityForwardingLoginCoordinator<Object> coordinator(
            ForwardedIdentityApplier<Object> applier
    ) throws Exception {
        VelocityForwardingLoginSupport support = new VelocityForwardingLoginSupport(
                codec(),
                VelocityForwardingConfig.secureDefaults()
        );
        return new VelocityForwardingLoginCoordinator<>(
                support.beginSession(73),
                applier
        );
    }

    private static VelocityForwardingCodec codec() throws Exception {
        return new VelocityForwardingCodec(
                SECRET,
                VelocityForwardingConfig.secureDefaults()
        );
    }

    private static ForwardedPlayer player() throws Exception {
        return new ForwardedPlayer(
                1,
                InetAddress.getByName("127.0.0.1"),
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                "Alex_01",
                List.of(ProfileProperty.unsigned("helstrea", "velocity"))
        );
    }

    private static void expect(ForwardingError error, ThrowingAction action)
            throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected forwarding error " + error);
        } catch (VelocityForwardingException exception) {
            check(
                    exception.error() == error,
                    "expected " + error + " but received " + exception.error()
            );
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
