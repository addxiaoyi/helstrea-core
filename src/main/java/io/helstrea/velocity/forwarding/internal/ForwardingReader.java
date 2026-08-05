package io.helstrea.velocity.forwarding.internal;

import io.helstrea.velocity.forwarding.ForwardingError;
import io.helstrea.velocity.forwarding.VelocityForwardingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

public final class ForwardingReader {
    private final byte[] bytes;
    private int index;

    public ForwardingReader(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    public int remaining() {
        return bytes.length - index;
    }

    public int readVarInt() throws VelocityForwardingException {
        int value = 0;
        int position = 0;

        while (position < 32) {
            int current = readUnsignedByte();
            value |= (current & 0x7f) << position;
            if ((current & 0x80) == 0) {
                return value;
            }
            position += 7;
        }

        throw malformed("VarInt exceeds five bytes");
    }

    public String readString(int maxBytes, String field) throws VelocityForwardingException {
        int length = readVarInt();
        if (length < 0 || length > maxBytes) {
            throw malformed(field + " length exceeds limit");
        }
        byte[] value = readBytes(length);
        String decoded = new String(value, StandardCharsets.UTF_8);
        if (decoded.getBytes(StandardCharsets.UTF_8).length != length) {
            throw malformed(field + " is not valid UTF-8");
        }
        return decoded;
    }

    public boolean readBoolean() throws VelocityForwardingException {
        int value = readUnsignedByte();
        if (value != 0 && value != 1) {
            throw malformed("Boolean value must be 0 or 1");
        }
        return value == 1;
    }

    public UUID readUuid() throws VelocityForwardingException {
        return new UUID(readLong(), readLong());
    }

    public byte[] readRemainingBytes() throws VelocityForwardingException {
        return readBytes(remaining());
    }

    private long readLong() throws VelocityForwardingException {
        byte[] value = readBytes(Long.BYTES);
        long decoded = 0;
        for (byte current : value) {
            decoded = (decoded << 8) | (current & 0xffL);
        }
        return decoded;
    }

    private int readUnsignedByte() throws VelocityForwardingException {
        if (index >= bytes.length) {
            throw malformed("Unexpected end of payload");
        }
        return bytes[index++] & 0xff;
    }

    private byte[] readBytes(int length) throws VelocityForwardingException {
        if (length < 0 || length > remaining()) {
            throw malformed("Unexpected end of payload");
        }
        byte[] value = Arrays.copyOfRange(bytes, index, index + length);
        index += length;
        return value;
    }

    private static VelocityForwardingException malformed(String message) {
        return new VelocityForwardingException(ForwardingError.MALFORMED_PAYLOAD, message);
    }
}
