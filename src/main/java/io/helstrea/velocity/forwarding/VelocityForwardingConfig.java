package io.helstrea.velocity.forwarding;

public record VelocityForwardingConfig(
        boolean enabled,
        boolean requireProxy,
        int maxPayloadBytes,
        int maxProperties,
        int maxPropertyValueBytes
) {
    public static final int DEFAULT_MAX_PAYLOAD_BYTES = 65_536;
    public static final int DEFAULT_MAX_PROPERTIES = 64;
    public static final int DEFAULT_MAX_PROPERTY_VALUE_BYTES = 16_384;

    public VelocityForwardingConfig {
        if (maxPayloadBytes < 64 || maxPayloadBytes > 1_048_576) {
            throw new IllegalArgumentException("maxPayloadBytes must be between 64 and 1048576");
        }
        if (maxProperties < 0 || maxProperties > 1024) {
            throw new IllegalArgumentException("maxProperties must be between 0 and 1024");
        }
        if (maxPropertyValueBytes < 1 || maxPropertyValueBytes > 262_144) {
            throw new IllegalArgumentException("maxPropertyValueBytes must be between 1 and 262144");
        }
    }

    public static VelocityForwardingConfig secureDefaults() {
        return new VelocityForwardingConfig(
                true,
                true,
                DEFAULT_MAX_PAYLOAD_BYTES,
                DEFAULT_MAX_PROPERTIES,
                DEFAULT_MAX_PROPERTY_VALUE_BYTES
        );
    }
}
