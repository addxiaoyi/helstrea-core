package io.helstrea.velocity.forwarding;

import io.helstrea.velocity.forwarding.security.ForwardingSecretLoader;
import io.helstrea.velocity.forwarding.security.TrustedProxyPolicy;
import io.helstrea.velocity.forwarding.security.VelocityForwardingEndpoint;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class VelocityForwardingBackend implements AutoCloseable {
    private final VelocityForwardingCodec codec;
    private final VelocityForwardingEndpoint endpoint;
    private boolean closed;

    private VelocityForwardingBackend(
            VelocityForwardingCodec codec,
            VelocityForwardingEndpoint endpoint
    ) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    }

    public static VelocityForwardingBackend create(
            Path secretFile,
            List<String> trustedProxyCidrs,
            VelocityForwardingConfig config
    ) throws VelocityForwardingException {
        Objects.requireNonNull(config, "config");
        TrustedProxyPolicy trustedProxies =
                TrustedProxyPolicy.fromCidrs(trustedProxyCidrs);
        byte[] secret = ForwardingSecretLoader.load(secretFile);
        try {
            VelocityForwardingCodec codec = new VelocityForwardingCodec(secret, config);
            VelocityForwardingLoginSupport loginSupport =
                    new VelocityForwardingLoginSupport(codec, config);
            return new VelocityForwardingBackend(
                    codec,
                    new VelocityForwardingEndpoint(loginSupport, trustedProxies)
            );
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    public synchronized <C> VelocityForwardingLoginCoordinator<C> beginLogin(
            InetAddress connectionSource,
            int transactionId,
            ForwardedIdentityApplier<C> identityApplier
    ) throws VelocityForwardingException {
        requireOpen();
        VelocityForwardingSession session = endpoint.beginSession(
                Objects.requireNonNull(connectionSource, "connectionSource"),
                transactionId
        );
        return new VelocityForwardingLoginCoordinator<>(
                session,
                Objects.requireNonNull(identityApplier, "identityApplier")
        );
    }

    public synchronized boolean closed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        codec.close();
    }

    private void requireOpen() throws VelocityForwardingException {
        if (closed) {
            throw new VelocityForwardingException(
                    ForwardingError.FORWARDING_CLOSED,
                    "Velocity forwarding backend is closed"
            );
        }
    }
}
