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
        List<ProfileProperty> properties
) {
    public ForwardedPlayer {
        address = Objects.requireNonNull(address, "address");
        playerId = Objects.requireNonNull(playerId, "playerId");
        username = Objects.requireNonNull(username, "username");
        properties = List.copyOf(Objects.requireNonNull(properties, "properties"));
    }
}
