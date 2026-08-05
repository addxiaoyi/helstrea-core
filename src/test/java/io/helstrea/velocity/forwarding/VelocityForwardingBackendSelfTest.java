package io.helstrea.velocity.forwarding;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class VelocityForwardingBackendSelfTest {
    private VelocityForwardingBackendSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        Path secretFile = Files.createTempFile("helstrea-backend-", ".secret");
        try {
            Files.writeString(secretFile, "backend secret\n", StandardCharsets.UTF_8);
            acceptsTrustedProxyAndAppliesIdentity(secretFile);
            rejectsUntrustedProxyBeforeChallenge(secretFile);
            rejectsNewAndPendingLoginsAfterClose(secretFile);
            System.out.println("Velocity forwarding backend self-test: PASS");
        } finally {
            Files.deleteIfExists(secretFile);
        }
    }

    private static void acceptsTrustedProxyAndAppliesIdentity(Path secretFile)
            throws Exception {
        VelocityForwardingConfig config = VelocityForwardingConfig.secureDefaults();
        try (VelocityForwardingBackend backend = VelocityForwardingBackend.create(
                secretFile,
                List.of("10.0.0.0/8"),
                config
        ); VelocityForwardingCodec proxyCodec = VelocityForwardingCodec.fromUtf8Secret(
                "backend secret",
                config
        )) {
            AtomicReference<ForwardedPlayer> applied = new AtomicReference<>();
            StringBuilder connection = new StringBuilder();
            VelocityForwardingLoginCoordinator<StringBuilder> coordinator = backend.beginLogin(
                    InetAddress.getByName("10.8.0.3"),
                    91,
                    (currentConnection, player) -> {
                        currentConnection.append(player.username());
                        applied.set(player);
                    }
            );

            check(
                    coordinator.challenge().payload().length == 1
                            && coordinator.challenge().payload()[0] == 4,
                    "secure backend must advertise modern forwarding version 4"
            );

            ForwardedPlayer expected = new ForwardedPlayer(
                    4,
                    InetAddress.getByName("198.51.100.27"),
                    UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                    "Alex_01",
                    List.of(ProfileProperty.unsigned("textures", "value"))
            );
            byte[] response = proxyCodec.encode(expected);
            ForwardedPlayer actual = coordinator.acceptResponse(connection, 91, response);

            check(expected.equals(actual), "verified identity must be returned");
            check(expected.equals(applied.get()), "verified identity must be atomically applied");
            check("Alex_01".contentEquals(connection), "platform connection must be updated");
        }
    }

    private static void rejectsUntrustedProxyBeforeChallenge(Path secretFile)
            throws Exception {
        try (VelocityForwardingBackend backend = VelocityForwardingBackend.create(
                secretFile,
                List.of("10.0.0.0/8"),
                VelocityForwardingConfig.secureDefaults()
        )) {
            expect(
                    ForwardingError.UNTRUSTED_PROXY,
                    () -> backend.beginLogin(
                            InetAddress.getByName("203.0.113.10"),
                            92,
                            (connection, player) -> {
                                throw new AssertionError("identity applier must not be invoked");
                            }
                    )
            );
        }
    }

    private static void rejectsNewAndPendingLoginsAfterClose(Path secretFile)
            throws Exception {
        VelocityForwardingConfig config = VelocityForwardingConfig.secureDefaults();
        VelocityForwardingBackend backend = VelocityForwardingBackend.create(
                secretFile,
                List.of("10.0.0.0/8"),
                config
        );
        AtomicReference<ForwardedPlayer> applied = new AtomicReference<>();
        VelocityForwardingLoginCoordinator<Object> pending = backend.beginLogin(
                InetAddress.getByName("10.0.0.4"),
                93,
                (connection, player) -> applied.set(player)
        );
        byte[] response;
        try (VelocityForwardingCodec proxyCodec = VelocityForwardingCodec.fromUtf8Secret(
                "backend secret",
                config
        )) {
            response = proxyCodec.encode(new ForwardedPlayer(
                    4,
                    InetAddress.getByName("198.51.100.28"),
                    UUID.fromString("123e4567-e89b-12d3-a456-426614174001"),
                    "Steve_02",
                    List.of()
            ));
        }

        backend.close();
        backend.close();
        check(backend.closed(), "backend close must be idempotent and observable");
        expect(
                ForwardingError.FORWARDING_CLOSED,
                () -> backend.beginLogin(
                        InetAddress.getByName("10.0.0.5"),
                        94,
                        (connection, player) -> {
                            throw new AssertionError("closed backend must not apply identity");
                        }
                )
        );
        expect(
                ForwardingError.FORWARDING_CLOSED,
                () -> pending.acceptResponse(new Object(), 93, response)
        );
        check(applied.get() == null, "pending login must not apply identity after shutdown");
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
