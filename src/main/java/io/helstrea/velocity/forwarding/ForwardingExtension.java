package io.helstrea.velocity.forwarding;

import java.util.Arrays;
import java.util.Objects;

public final class ForwardingExtension {
    private static final ForwardingExtension EMPTY = new ForwardingExtension(new byte[0]);

    private final byte[] bytes;

    private ForwardingExtension(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    public static ForwardingExtension empty() {
        return EMPTY;
    }

    public static ForwardingExtension of(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return bytes.length == 0 ? EMPTY : new ForwardingExtension(bytes);
    }

    public boolean isEmpty() {
        return bytes.length == 0;
    }

    public int size() {
        return bytes.length;
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ForwardingExtension extension
                && Arrays.equals(bytes, extension.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "ForwardingExtension[size=" + bytes.length + "]";
    }
}
