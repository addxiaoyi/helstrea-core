package io.helstrea.velocity.forwarding.security;

import io.helstrea.velocity.forwarding.ForwardingError;
import io.helstrea.velocity.forwarding.VelocityForwardingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;

public final class IpSubnet {
    private final byte[] network;
    private final int prefixLength;

    private IpSubnet(byte[] network, int prefixLength) {
        this.network = network.clone();
        this.prefixLength = prefixLength;
    }

    public static IpSubnet parse(String value) throws VelocityForwardingException {
        Objects.requireNonNull(value, "value");
        String candidate = value.trim();
        if (candidate.isEmpty()) {
            throw invalid("Trusted proxy CIDR is empty", null);
        }

        int slash = candidate.indexOf('/');
        if (slash != candidate.lastIndexOf('/')) {
            throw invalid("Trusted proxy CIDR contains multiple prefix separators", null);
        }
        String addressPart = slash < 0 ? candidate : candidate.substring(0, slash);
        InetAddress address = parseLiteral(addressPart);
        byte[] bytes = address.getAddress();
        int addressBits = bytes.length * Byte.SIZE;
        int prefix = slash < 0
                ? addressBits
                : parsePrefix(candidate.substring(slash + 1), addressBits);

        byte[] network = bytes.clone();
        maskHostBits(network, prefix);
        return new IpSubnet(network, prefix);
    }

    public boolean contains(InetAddress address) {
        Objects.requireNonNull(address, "address");
        byte[] candidate = address.getAddress();
        if (candidate.length != network.length) {
            return false;
        }
        int fullBytes = prefixLength / Byte.SIZE;
        int remainingBits = prefixLength % Byte.SIZE;
        for (int index = 0; index < fullBytes; index++) {
            if (candidate[index] != network[index]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xff << (Byte.SIZE - remainingBits);
        return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
    }

    public int prefixLength() {
        return prefixLength;
    }

    public byte[] networkAddress() {
        return network.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof IpSubnet subnet
                && prefixLength == subnet.prefixLength
                && Arrays.equals(network, subnet.network);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(network) + prefixLength;
    }

    @Override
    public String toString() {
        try {
            return InetAddress.getByAddress(network).getHostAddress() + '/' + prefixLength;
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("Stored subnet has an invalid address length", exception);
        }
    }

    private static InetAddress parseLiteral(String value) throws VelocityForwardingException {
        if (value.isEmpty() || value.indexOf('%') >= 0) {
            throw invalid("Trusted proxy address is not a plain IP literal", null);
        }
        if (value.indexOf(':') >= 0) {
            if (value.indexOf('.') >= 0) {
                throw invalid("IPv4-mapped IPv6 literals are not supported", null);
            }
            try {
                InetAddress address = InetAddress.getByName(value);
                if (address.getAddress().length != 16) {
                    throw invalid("Trusted proxy address is not an IPv6 literal", null);
                }
                return address;
            } catch (UnknownHostException exception) {
                throw invalid("Trusted proxy IPv6 literal is invalid", exception);
            }
        }

        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            throw invalid("Trusted proxy address is not an IPv4 literal", null);
        }
        byte[] bytes = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 3) {
                throw invalid("Trusted proxy IPv4 octet is invalid", null);
            }
            try {
                int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) {
                    throw invalid("Trusted proxy IPv4 octet is out of range", null);
                }
                bytes[index] = (byte) octet;
            } catch (NumberFormatException exception) {
                throw invalid("Trusted proxy IPv4 octet is invalid", exception);
            }
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException exception) {
            throw invalid("Trusted proxy IPv4 literal is invalid", exception);
        }
    }

    private static int parsePrefix(String value, int addressBits)
            throws VelocityForwardingException {
        if (value.isEmpty()) {
            throw invalid("Trusted proxy CIDR prefix is empty", null);
        }
        try {
            int prefix = Integer.parseInt(value);
            if (prefix < 0 || prefix > addressBits) {
                throw invalid("Trusted proxy CIDR prefix is out of range", null);
            }
            return prefix;
        } catch (NumberFormatException exception) {
            throw invalid("Trusted proxy CIDR prefix is invalid", exception);
        }
    }

    private static void maskHostBits(byte[] bytes, int prefixLength) {
        int fullBytes = prefixLength / Byte.SIZE;
        int remainingBits = prefixLength % Byte.SIZE;
        if (remainingBits != 0) {
            int mask = 0xff << (Byte.SIZE - remainingBits);
            bytes[fullBytes] = (byte) (bytes[fullBytes] & mask);
            fullBytes++;
        }
        Arrays.fill(bytes, fullBytes, bytes.length, (byte) 0);
    }

    private static VelocityForwardingException invalid(String message, Throwable cause) {
        return cause == null
                ? new VelocityForwardingException(ForwardingError.INVALID_PROXY_CIDR, message)
                : new VelocityForwardingException(
                        ForwardingError.INVALID_PROXY_CIDR,
                        message,
                        cause
                );
    }
}
