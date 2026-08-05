package io.helstrea.velocity.forwarding.security;

import io.helstrea.velocity.forwarding.VelocityForwardingException;
import io.helstrea.velocity.forwarding.VelocityForwardingLoginSupport;
import io.helstrea.velocity.forwarding.VelocityForwardingSession;
import java.net.InetAddress;
import java.util.Objects;

public final class VelocityForwardingEndpoint {
    private final VelocityForwardingLoginSupport loginSupport;
    private final TrustedProxyPolicy trustedProxies;

    public VelocityForwardingEndpoint(
            VelocityForwardingLoginSupport loginSupport,
            TrustedProxyPolicy trustedProxies
    ) {
        this.loginSupport = Objects.requireNonNull(loginSupport, "loginSupport");
        this.trustedProxies = Objects.requireNonNull(trustedProxies, "trustedProxies");
    }

    public VelocityForwardingSession beginSession(
            InetAddress connectionSource,
            int transactionId
    ) throws VelocityForwardingException {
        trustedProxies.requireTrusted(
                Objects.requireNonNull(connectionSource, "connectionSource")
        );
        return loginSupport.beginSession(transactionId);
    }
}
