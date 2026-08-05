package io.helstrea.velocity.forwarding;

@FunctionalInterface
public interface ForwardedIdentityApplier<C> {
    void apply(C connection, ForwardedPlayer player)
            throws VelocityForwardingException;
}
