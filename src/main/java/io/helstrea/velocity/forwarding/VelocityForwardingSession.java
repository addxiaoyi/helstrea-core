package io.helstrea.velocity.forwarding;

import java.util.Objects;

public final class VelocityForwardingSession {
    private final VelocityForwardingLoginSupport loginSupport;
    private final VelocityForwardingLoginSupport.LoginChallenge challenge;
    private boolean consumed;

    VelocityForwardingSession(
            VelocityForwardingLoginSupport loginSupport,
            int transactionId
    ) throws VelocityForwardingException {
        this.loginSupport = Objects.requireNonNull(loginSupport, "loginSupport");
        this.challenge = loginSupport.createChallenge(transactionId);
    }

    public VelocityForwardingLoginSupport.LoginChallenge challenge() {
        return challenge;
    }

    public synchronized ForwardedPlayer acceptResponse(
            int responseTransactionId,
            byte[] response
    ) throws VelocityForwardingException {
        if (responseTransactionId != challenge.transactionId()) {
            throw new VelocityForwardingException(
                    ForwardingError.TRANSACTION_MISMATCH,
                    "Velocity forwarding response transaction does not match the challenge"
            );
        }
        if (consumed) {
            throw new VelocityForwardingException(
                    ForwardingError.RESPONSE_ALREADY_CONSUMED,
                    "Velocity forwarding response has already been consumed"
            );
        }
        consumed = true;
        return loginSupport.acceptResponse(response);
    }

    public synchronized boolean consumed() {
        return consumed;
    }
}
