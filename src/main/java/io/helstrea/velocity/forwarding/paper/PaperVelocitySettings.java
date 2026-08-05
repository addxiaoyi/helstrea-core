package io.helstrea.velocity.forwarding.paper;

public record PaperVelocitySettings(
        boolean serverOnlineMode,
        boolean bungeeCordForwardingEnabled,
        boolean velocityForwardingEnabled,
        boolean paperVelocityOnlineMode,
        boolean proxyOnlineMode,
        boolean forwardingSecretPresent,
        boolean forwardingSecretMatches
) {
}
