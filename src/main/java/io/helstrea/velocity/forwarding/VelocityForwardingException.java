package io.helstrea.velocity.forwarding;

public final class VelocityForwardingException extends Exception {
    private static final long serialVersionUID = 1L;

    private final ForwardingError error;

    public VelocityForwardingException(ForwardingError error, String message) {
        super(message);
        this.error = error;
    }

    public VelocityForwardingException(ForwardingError error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    public ForwardingError error() {
        return error;
    }
}
