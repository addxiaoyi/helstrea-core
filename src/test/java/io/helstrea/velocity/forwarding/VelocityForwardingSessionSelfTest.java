package io.helstrea.velocity.forwarding;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class VelocityForwardingSessionSelfTest {
    private static final byte[] SECRET =
            "correct horse battery staple".getBytes(StandardCharsets.UTF_8);

    private VelocityForwardingSessionSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        acceptsMatchingResponseOnce();
        rejectsTransactionMismatch();
        rejectsRepeatedResponse();
        rejectsMissingRequiredResponse();
        System.out.println("Velocity forwarding session self-test: PASS");
    }

    private static void acceptsMatchingResponseOnce() throws Exception {
        VelocityForwardingCodec codec = codec();
        VelocityForwardingSession session = support(codec).beginSession(41);
        ForwardedPlayer expected = player();
        ForwardedPlayer actual = session.acceptResponse(41, codec.encodeV1(expected));
        check(expected.equals(actual), "matching response must authenticate the player");
        check(session.consumed(), "successful response must consume the session");
    }

    private static void rejectsTransactionMismatch() throws Exception {
        VelocityForwardingCodec codec = codec();
        VelocityForwardingSession session = support(codec).beginSession(41);
        expect(
                ForwardingError.TRANSACTION_MISMATCH,
                () -> session.acceptResponse(42, codec.encodeV1(player()))
        );
        check(!session.consumed(), "mismatched response must not consume the session");
    }

    private static void rejectsRepeatedResponse() throws Exception {
        VelocityForwardingCodec codec = codec();
        VelocityForwardingSession session = support(codec).beginSession(41);
        byte[] response = codec.encodeV1(player());
        session.acceptResponse(41, response);
        expect(
                ForwardingError.RESPONSE_ALREADY_CONSUMED,
                () -> session.acceptResponse(41, response)
        );
    }

    private static void rejectsMissingRequiredResponse() throws Exception {
        VelocityForwardingSession session = support(codec()).beginSession(41);
        expect(
                ForwardingError.MISSING_RESPONSE,
                () -> session.acceptResponse(41, null)
        );
        check(session.consumed(), "matched missing response must consume the session");
    }

    private static VelocityForwardingLoginSupport support(VelocityForwardingCodec codec) {
        return new VelocityForwardingLoginSupport(
                codec,
                VelocityForwardingConfig.secureDefaults()
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
