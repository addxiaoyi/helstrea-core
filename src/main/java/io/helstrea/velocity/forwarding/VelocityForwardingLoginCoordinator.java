package io.helstrea.velocity.forwarding;

import java.util.Objects;

public final class VelocityForwardingLoginCoordinator<C> {
    private final VelocityForwardingSession session;
    private final ForwardedIdentityApplier<C> identityApplier;

    public VelocityForwardingLoginCoordinator(
            VelocityForwardingSession session,
            ForwardedIdentityApplier<C> identityApplier
    ) {
        this.session = Objects.requireNonNull(session, "session");
        this.identityApplier = Objects.requireNonNull(identityApplier, "identityApplier");
    }

    public VelocityForwardingLoginSupport.LoginChallenge challenge() {
        return session.challenge();
    }

    public ForwardedPlayer acceptResponse(
            C connection,
            int responseTransactionId,
            byte[] response
    ) throws VelocityForwardingException {
        Objects.requireNonNull(connection, "connection");
        ForwardedPlayer player = session.acceptResponse(responseTransactionId, response);
        if (player != null) {
            identityApplier.apply(connection, player);
        }
        return player;
    }

    public boolean consumed() {
        return session.consumed();
    }
}
