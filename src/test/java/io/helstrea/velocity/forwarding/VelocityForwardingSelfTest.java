package io.helstrea.velocity.forwarding;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class VelocityForwardingSelfTest {
    private static final byte[] SECRET =
            "correct horse battery staple".getBytes(StandardCharsets.UTF_8);

    private VelocityForwardingSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        roundTripSignedProfile();
        rejectsTamperedPayload();
        rejectsWrongSecret();
        rejectsTruncatedPayload();
        rejectsUnsupportedVersion();
        rejectsTrailingData();
        rejectsInvalidUsername();
        createsVersionOneChallenge();
        rejectsMissingProxyResponse();
        System.out.println("Velocity forwarding self-test: PASS");
    }

    private static void roundTripSignedProfile() throws Exception {
        VelocityForwardingCodec codec = codec(SECRET);
        ForwardedPlayer expected = player("Alex_01");
        ForwardedPlayer actual = codec.decode(codec.encodeV1(expected));
        check(expected.equals(actual), "round trip must preserve player information");
    }

    private static void rejectsTamperedPayload() throws Exception {
        VelocityForwardingCodec codec = codec(SECRET);
        byte[] encoded = codec.encodeV1(player("Alex_01"));
        encoded[encoded.length - 1] ^= 1;
        expect(ForwardingError.INVALID_SIGNATURE, () -> codec.decode(encoded));
    }

    private static void rejectsWrongSecret() throws Exception {
        byte[] encoded = codec(SECRET).encodeV1(player("Alex_01"));
        expect(
                ForwardingError.INVALID_SIGNATURE,
                () -> codec("different secret".getBytes(StandardCharsets.UTF_8))
                        .decode(encoded)
        );
    }

    private static void rejectsTruncatedPayload() throws Exception {
        expect(
                ForwardingError.PAYLOAD_TOO_SMALL,
                () -> codec(SECRET).decode(new byte[8])
        );
    }

    private static void rejectsUnsupportedVersion() throws Exception {
        ForwardedPlayer unsupported = new ForwardedPlayer(
                2,
                InetAddress.getByName("127.0.0.1"),
                UUID.randomUUID(),
                "Alex_01",
                List.of()
        );
        expect(
                ForwardingError.UNSUPPORTED_VERSION,
                () -> codec(SECRET).encodeV1(unsupported)
        );
    }

    private static void rejectsTrailingData() throws Exception {
        VelocityForwardingCodec codec = codec(SECRET);
        byte[] valid = codec.encodeV1(player("Alex_01"));
        byte[] body = new byte[
                valid.length - VelocityForwardingCodec.SIGNATURE_BYTES + 1
        ];
        System.arraycopy(
                valid,
                VelocityForwardingCodec.SIGNATURE_BYTES,
                body,
                0,
                valid.length - VelocityForwardingCodec.SIGNATURE_BYTES
        );
        body[body.length - 1] = 42;
        expect(
                ForwardingError.TRAILING_DATA,
                () -> codec.decode(signRaw(SECRET, body))
        );
    }

    private static void rejectsInvalidUsername() throws Exception {
        ForwardedPlayer invalid = new ForwardedPlayer(
                1,
                InetAddress.getByName("127.0.0.1"),
                UUID.randomUUID(),
                "bad name",
                List.of()
        );
        expect(
                ForwardingError.INVALID_USERNAME,
                () -> codec(SECRET).encodeV1(invalid)
        );
    }

    private static void createsVersionOneChallenge() throws Exception {
        VelocityForwardingConfig config = VelocityForwardingConfig.secureDefaults();
        VelocityForwardingLoginSupport support =
                new VelocityForwardingLoginSupport(codec(SECRET), config);
        var challenge = support.createChallenge(19);
        check(challenge.transactionId() == 19, "transaction ID must be preserved");
        check(
                VelocityForwardingCodec.PLAYER_INFO_CHANNEL.equals(challenge.channel()),
                "challenge channel must be velocity:player_info"
        );
        check(
                challenge.payload().length == 1 && challenge.payload()[0] == 1,
                "challenge must advertise forwarding version 1"
        );
    }

    private static void rejectsMissingProxyResponse() throws Exception {
        VelocityForwardingConfig config = VelocityForwardingConfig.secureDefaults();
        VelocityForwardingLoginSupport support =
                new VelocityForwardingLoginSupport(codec(SECRET), config);
        expect(
                ForwardingError.MISSING_RESPONSE,
                () -> support.acceptResponse(null)
        );
    }

    private static ForwardedPlayer player(String username) throws Exception {
        return new ForwardedPlayer(
                1,
                InetAddress.getByName("2001:db8::10"),
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                username,
                List.of(
                        ProfileProperty.signed(
                                "textures",
                                "base64-value",
                                "base64-signature"
                        ),
                        ProfileProperty.unsigned("helstrea", "native-velocity")
                )
        );
    }

    private static VelocityForwardingCodec codec(byte[] secret) throws Exception {
        return new VelocityForwardingCodec(
                secret,
                VelocityForwardingConfig.secureDefaults()
        );
    }

    private static byte[] signRaw(byte[] secret, byte[] body) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"));
        byte[] signature = mac.doFinal(body);
        byte[] signed = new byte[signature.length + body.length];
        System.arraycopy(signature, 0, signed, 0, signature.length);
        System.arraycopy(body, 0, signed, signature.length, body.length);
        return signed;
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
