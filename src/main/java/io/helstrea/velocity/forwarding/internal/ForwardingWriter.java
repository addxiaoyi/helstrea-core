package io.helstrea.velocity.forwarding.internal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ForwardingWriter {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    public void writeVarInt(int value) {
        int remaining = value;
        do {
            int current = remaining & 0x7f;
            remaining >>>= 7;
            if (remaining != 0) {
                current |= 0x80;
            }
            bytes.write(current);
        } while (remaining != 0);
    }

    public void writeString(String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(encoded.length);
        bytes.writeBytes(encoded);
    }

    public void writeBoolean(boolean value) {
        bytes.write(value ? 1 : 0);
    }

    public void writeUuid(UUID value) {
        writeLong(value.getMostSignificantBits());
        writeLong(value.getLeastSignificantBits());
    }

    public byte[] toByteArray() {
        return bytes.toByteArray();
    }

    private void writeLong(long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            bytes.write((int) (value >>> shift) & 0xff);
        }
    }
}
