package io.helstrea.velocity.forwarding;

public enum VelocityForwardingVersion {
    MODERN_DEFAULT(1, false),
    MODERN_FORWARDING_WITH_KEY(2, true),
    MODERN_FORWARDING_WITH_KEY_V2(3, true),
    MODERN_LAZY_SESSION(4, false);

    private final int id;
    private final boolean hasPlatformExtension;

    VelocityForwardingVersion(int id, boolean hasPlatformExtension) {
        this.id = id;
        this.hasPlatformExtension = hasPlatformExtension;
    }

    public int id() {
        return id;
    }

    public boolean hasPlatformExtension() {
        return hasPlatformExtension;
    }

    public static VelocityForwardingVersion fromId(int id)
            throws VelocityForwardingException {
        for (VelocityForwardingVersion version : values()) {
            if (version.id == id) {
                return version;
            }
        }
        throw new VelocityForwardingException(
                ForwardingError.UNSUPPORTED_VERSION,
                "Unsupported Velocity forwarding version: " + id
        );
    }
}
