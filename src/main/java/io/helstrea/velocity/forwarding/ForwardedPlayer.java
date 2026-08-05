package io.helstrea.velocity.forwarding;

import java.net.InetAddress;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ForwardedPlayer(
        int forwardingVersion,
        InetAddress address,
        UUID playerId,
        String username,
        List<ProfileProperty> properties,
        ForwardingExtension extension
) {
    public ForwardedPlayer {
        VelocityForwardingVersion.fromId(forwardingVersion);
        address = Objects.requireNonNull(address, "address");
        playerId = Objects.requireNonNull(playerId, "playerId");
        username = Objects.requireNonNull(username, "username");
        properties = List.copyOf(Objects.requireNonNull(properties, "properties"));
        extension = Objects.requireNonNull(extension, "extension");
    }

    public ForwardedPlayer(
            int forwardingVersion,
            InetAddress address,
            UUID playerId,
            String username,
            List<ProfileProperty> properties
    ) {
        this(
                forwardingVersion,
                address,
                playerId,
                username,
                properties,
                ForwardingExtension.empty()
        );
    }

    public VelocityForwardingVersion version() {
        try {
            return VelocityForwardingVersion.fromId(forwardingVersion);
        } catch (VelocityForwardingException exception) {
            throw new IllegalStateException("Forwarded player contains an invalid version", exception);
        }
    }
}
