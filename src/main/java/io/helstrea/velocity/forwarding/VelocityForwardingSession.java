package io.helstrea.velocity.forwarding;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class VelocityForwardingSession {
    private final VelocityForwardingLoginSupport loginSupport;
    private final VelocityForwardingLoginSupport.LoginChallenge challenge;
    private final long deadlineNanos;
    private final LongSupplier ticker;
    private boolean consumed;

    VelocityForwardingSession(
            VelocityForwardingLoginSupport loginSupport,
            int transactionId,
            long responseTimeoutMillis,
            LongSupplier ticker
    ) throws VelocityForwardingException {
        this.loginSupport = Objects.requireNonNull(loginSupport, "loginSupport");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.challenge = loginSupport.createChallenge(transactionId);
        this.deadlineNanos = ticker.getAsLong()
                + TimeUnit.MILLISECONDS.toNanos(responseTimeoutMillis);
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
        if (expiredAt(ticker.getAsLong())) {
            throw new VelocityForwardingException(
                    ForwardingError.RESPONSE_EXPIRED,
                    "Velocity forwarding response arrived after the login deadline"
            );
        }
        return loginSupport.acceptResponse(response);
    }

    public synchronized boolean consumed() {
        return consumed;
    }

    public synchronized boolean expired() {
        return expiredAt(ticker.getAsLong());
    }

    private boolean expiredAt(long nowNanos) {
        return nowNanos - deadlineNanos >= 0;
    }
}
