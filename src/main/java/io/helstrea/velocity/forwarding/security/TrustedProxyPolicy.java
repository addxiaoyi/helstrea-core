package io.helstrea.velocity.forwarding.security;

import io.helstrea.velocity.forwarding.ForwardingError;
import io.helstrea.velocity.forwarding.VelocityForwardingException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TrustedProxyPolicy {
    private final List<IpSubnet> subnets;

    private TrustedProxyPolicy(List<IpSubnet> subnets) {
        this.subnets = List.copyOf(subnets);
    }

    public static TrustedProxyPolicy fromCidrs(List<String> cidrs)
            throws VelocityForwardingException {
        Objects.requireNonNull(cidrs, "cidrs");
        if (cidrs.isEmpty()) {
            throw new VelocityForwardingException(
                    ForwardingError.EMPTY_TRUSTED_PROXY_SET,
                    "At least one trusted Velocity proxy CIDR is required"
            );
        }
        List<IpSubnet> parsed = new ArrayList<>(cidrs.size());
        for (String cidr : cidrs) {
            parsed.add(IpSubnet.parse(Objects.requireNonNull(cidr, "cidr")));
        }
        return new TrustedProxyPolicy(parsed);
    }

    public boolean isTrusted(InetAddress address) {
        Objects.requireNonNull(address, "address");
        for (IpSubnet subnet : subnets) {
            if (subnet.contains(address)) {
                return true;
            }
        }
        return false;
    }

    public void requireTrusted(InetAddress address) throws VelocityForwardingException {
        if (!isTrusted(address)) {
            throw new VelocityForwardingException(
                    ForwardingError.UNTRUSTED_PROXY,
                    "Connection source is not an approved Velocity proxy: "
                            + address.getHostAddress()
            );
        }
    }

    public List<IpSubnet> subnets() {
        return subnets;
    }
}
