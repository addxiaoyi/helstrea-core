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

    public VelocityForwardingSession beginSession(int transactionId)
            throws VelocityForwardingException {
        return new VelocityForwardingSession(this, transactionId);
    }

    public LoginChallenge createChallenge(int transactionId)
            throws VelocityForwardingException {
        if (!config.enabled()) {
            throw new VelocityForwardingException(
                    ForwardingError.FORWARDING_DISABLED,
                    "Velocity forwarding is disabled"
            );
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
                        ForwardingError.MISSING_RESPONSE,
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
