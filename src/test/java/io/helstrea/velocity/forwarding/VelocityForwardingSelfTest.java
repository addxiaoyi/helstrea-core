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
        roundTripLazySession();
        preservesSignedKeyExtension();
        rejectsMissingKeyExtension();
        rejectsTamperedPayload();
        rejectsWrongSecret();
        rejectsTruncatedPayload();
        rejectsUnsupportedVersionForV1Encoder();
        rejectsVersionAboveAdvertisedMaximum();
        rejectsTrailingData();
        rejectsInvalidUsername();
        createsVersionFourChallenge();
        createsConfiguredVersionOneChallenge();
        rejectsMissingProxyResponse();
        System.out.println("Velocity forwarding self-test: PASS");
    }

    private static void roundTripSignedProfile() throws Exception {
        VelocityForwardingCodec codec = codec(SECRET);
        ForwardedPlayer expected = player(1, ForwardingExtension.empty(), "Alex_01");
        ForwardedPlayer actual = codec.decode(codec.encodeV1(expected));
        check(expected.equals(actual), "v1 round trip must preserve player information");
    }

    private static void roundTripLazySession() throws Exception {
        VelocityForwardingCodec codec = codec(SECRET);
        ForwardedPlayer expected = player(4, ForwardingExtension.empty(), "Alex_01");
        ForwardedPlayer actual = codec.decode(codec.encode(expected));
        check(expected.equals(actual), "v4 round trip must preserve player information");
        check(
                actual.version() == VelocityForwardingVersion.MODERN_LAZY_SESSION,
                "v4 must decode as MODERN_LAZY_SESSION"
        );
    }

    private static void preservesSignedKeyExtension() throws Exception {
        VelocityForwardingCodec codec = codec(SECRET);
        ForwardingExtension extension = ForwardingExtension.of(new byte[] {1, 2, 3, 4, 5});
        ForwardedPlayer expected = player(3, extension, "Alex_01");
        ForwardedPlayer actual = codec.decode(codec.encode(expected));
        check(expected.equals(actual), "v3 extension bytes must survive a signed round trip");
        check(actual.extension().size() == 5, "v3 extension size must be preserved");
    }

    private static void rejectsMissingKeyExtension() throws Exception {
        ForwardedPlayer invalid = player(2, ForwardingExtension.empty(), "Alex_01");
        expect(ForwardingError.MALFORMED_PAYLOAD, () -> codec(SECRET).encode(invalid));
    }

    private static void rejectsTamperedPayload() throws Exception {
        VelocityForwardingCodec codec = codec(SECRET);
        byte[] encoded = codec.encodeV1(player(1, ForwardingExtension.empty(), "Alex_01"));
        encoded[encoded.length - 1] ^= 1;
        expect(ForwardingError.INVALID_SIGNATURE, () -> codec.decode(encoded));
    }

    private static void rejectsWrongSecret() throws Exception {
        byte[] encoded = codec(SECRET).encodeV1(
                player(1, ForwardingExtension.empty(), "Alex_01")
        );
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

    private static void rejectsUnsupportedVersionForV1Encoder() throws Exception {
        ForwardedPlayer unsupported = player(2, ForwardingExtension.of(new byte[] {1}), "Alex_01");
        expect(
                ForwardingError.UNSUPPORTED_VERSION,
                () -> codec(SECRET).encodeV1(unsupported)
        );
    }

    private static void rejectsVersionAboveAdvertisedMaximum() throws Exception {
        byte[] versionFour = codec(SECRET).encode(
                player(4, ForwardingExtension.empty(), "Alex_01")
        );
        VelocityForwardingConfig versionOneConfig = new VelocityForwardingConfig(
                true,
                true,
                VelocityForwardingConfig.DEFAULT_MAX_PAYLOAD_BYTES,
                VelocityForwardingConfig.DEFAULT_MAX_PROPERTIES,
                VelocityForwardingConfig.DEFAULT_MAX_PROPERTY_VALUE_BYTES,
                1
        );
        VelocityForwardingCodec versionOneCodec =
                new VelocityForwardingCodec(SECRET, versionOneConfig);
        expect(
                ForwardingError.UNSUPPORTED_VERSION,
                () -> versionOneCodec.decode(versionFour)
        );
    }

    private static void rejectsTrailingData() throws Exception {
        VelocityForwardingCodec codec = codec(SECRET);
        byte[] valid = codec.encodeV1(player(1, ForwardingExtension.empty(), "Alex_01"));
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
        ForwardedPlayer invalid = player(1, ForwardingExtension.empty(), "bad name");
        expect(
                ForwardingError.INVALID_USERNAME,
                () -> codec(SECRET).encodeV1(invalid)
        );
    }

    private static void createsVersionFourChallenge() throws Exception {
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
                challenge.payload().length == 1 && challenge.payload()[0] == 4,
                "default challenge must advertise forwarding version 4"
        );
    }

    private static void createsConfiguredVersionOneChallenge() throws Exception {
        VelocityForwardingConfig config = new VelocityForwardingConfig(
                true,
                true,
                VelocityForwardingConfig.DEFAULT_MAX_PAYLOAD_BYTES,
                VelocityForwardingConfig.DEFAULT_MAX_PROPERTIES,
                VelocityForwardingConfig.DEFAULT_MAX_PROPERTY_VALUE_BYTES,
                1
        );
        VelocityForwardingCodec codec = new VelocityForwardingCodec(SECRET, config);
        byte[] request = codec.createVersionRequest();
        check(
                request.length == 1 && request[0] == 1,
                "configured challenge must advertise forwarding version 1"
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

    private static ForwardedPlayer player(
            int version,
            ForwardingExtension extension,
            String username
    ) throws Exception {
        return new ForwardedPlayer(
                version,
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
                ),
                extension
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
