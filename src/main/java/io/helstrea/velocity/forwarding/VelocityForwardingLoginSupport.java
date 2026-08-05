package io.helstrea.velocity.forwarding;

import java.util.Objects;

public final class VelocityForwardingLoginSupport {
    private final VelocityForwardingCodec codec;
    private final VelocityForwardingConfig config;

    public VelocityForwardingLoginSupport(
            VelocityForwardingCodec codec,
            VelocityForwardingConfig config
    ) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.config = Objects.requireNonNull(config, "config");
    }

    public LoginChallenge createChallenge(int transactionId) {
        if (!config.enabled()) {
            throw new IllegalStateException("Velocity forwarding is disabled");
        }
        return new LoginChallenge(
                transactionId,
                VelocityForwardingCodec.PLAYER_INFO_CHANNEL,
                codec.createVersionRequest()
        );
    }

    public ForwardedPlayer acceptResponse(byte[] response)
            throws VelocityForwardingException {
        if (response == null) {
            if (config.requireProxy()) {
                throw new VelocityForwardingException(
                        ForwardingError.PAYLOAD_TOO_SMALL,
                        "This server requires a Velocity proxy connection"
                );
            }
            return null;
        }
        return codec.decode(response);
    }

    public record LoginChallenge(int transactionId, String channel, byte[] payload) {
        public LoginChallenge {
            Objects.requireNonNull(channel, "channel");
            payload = Objects.requireNonNull(payload, "payload").clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }
}
