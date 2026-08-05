package io.helstrea.velocity.forwarding;

import io.helstrea.velocity.forwarding.internal.ForwardingReader;
import io.helstrea.velocity.forwarding.internal.ForwardingWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class VelocityForwardingCodec implements AutoCloseable {
    public static final String PLAYER_INFO_CHANNEL = "velocity:player_info";
    public static final int MIN_SUPPORTED_FORWARDING_VERSION = 1;
    public static final int MAX_SUPPORTED_FORWARDING_VERSION = 4;
    public static final int SIGNATURE_BYTES = 32;

    private static final int MAX_ADDRESS_BYTES = 255;
    private static final int MAX_USERNAME_BYTES = 16;
    private static final int MAX_PROPERTY_NAME_BYTES = 64;

    private final byte[] secret;
    private final VelocityForwardingConfig config;
    private volatile boolean closed;

    public VelocityForwardingCodec(byte[] secret, VelocityForwardingConfig config)
            throws VelocityForwardingException {
        Objects.requireNonNull(secret, "secret");
        this.config = Objects.requireNonNull(config, "config");
        if (secret.length == 0) {
            throw new VelocityForwardingException(
                    ForwardingError.EMPTY_SECRET,
                    "Forwarding secret is empty"
            );
        }
        this.secret = secret.clone();
    }

    public static VelocityForwardingCodec fromUtf8Secret(
            String secret,
            VelocityForwardingConfig config
    ) throws VelocityForwardingException {
        Objects.requireNonNull(secret, "secret");
        return new VelocityForwardingCodec(secret.getBytes(StandardCharsets.UTF_8), config);
    }

    public byte[] createVersionRequest() throws VelocityForwardingException {
        requireOpen();
        ForwardingWriter writer = new ForwardingWriter();
        writer.writeVarInt(config.maxSupportedVersion());
        return writer.toByteArray();
    }

    public ForwardedPlayer decode(byte[] signedPayload) throws VelocityForwardingException {
        requireOpen();
        Objects.requireNonNull(signedPayload, "signedPayload");
        if (signedPayload.length < SIGNATURE_BYTES + 1) {
            throw new VelocityForwardingException(
                    ForwardingError.PAYLOAD_TOO_SMALL,
                    "Forwarding payload is too small"
            );
        }
        if (signedPayload.length > config.maxPayloadBytes()) {
            throw new VelocityForwardingException(
                    ForwardingError.PAYLOAD_TOO_LARGE,
                    "Forwarding payload exceeds configured limit"
            );
        }

        byte[] signature = new byte[SIGNATURE_BYTES];
        System.arraycopy(signedPayload, 0, signature, 0, SIGNATURE_BYTES);
        byte[] payload = new byte[signedPayload.length - SIGNATURE_BYTES];
        System.arraycopy(signedPayload, SIGNATURE_BYTES, payload, 0, payload.length);

        byte[] expected = sign(payload);
        if (!MessageDigest.isEqual(signature, expected)) {
            throw new VelocityForwardingException(
                    ForwardingError.INVALID_SIGNATURE,
                    "Unable to verify Velocity forwarding payload"
            );
        }

        ForwardingReader reader = new ForwardingReader(payload);
        VelocityForwardingVersion version = VelocityForwardingVersion.fromId(reader.readVarInt());
        if (version.id() > config.maxSupportedVersion()) {
            throw new VelocityForwardingException(
                    ForwardingError.UNSUPPORTED_VERSION,
                    "Velocity selected forwarding version "
                            + version.id()
                            + " but this backend advertised "
                            + config.maxSupportedVersion()
            );
        }

        InetAddress address = readAddress(reader);
        var playerId = reader.readUuid();
        String username = reader.readString(MAX_USERNAME_BYTES, "username");
        validateUsername(username);

        int propertyCount = reader.readVarInt();
        if (propertyCount < 0 || propertyCount > config.maxProperties()) {
            throw new VelocityForwardingException(
                    ForwardingError.TOO_MANY_PROPERTIES,
                    "Profile property count exceeds configured limit"
            );
        }

        List<ProfileProperty> properties = new ArrayList<>(propertyCount);
        for (int index = 0; index < propertyCount; index++) {
            String name = reader.readString(MAX_PROPERTY_NAME_BYTES, "property name");
            String value = reader.readString(config.maxPropertyValueBytes(), "property value");
            boolean signed = reader.readBoolean();
            properties.add(signed
                    ? ProfileProperty.signed(
                            name,
                            value,
                            reader.readString(
                                    config.maxPropertyValueBytes(),
                                    "property signature"
                            )
                    )
                    : ProfileProperty.unsigned(name, value));
        }

        ForwardingExtension extension = readExtension(version, reader);
        return new ForwardedPlayer(
                version.id(),
                address,
                playerId,
                username,
                properties,
                extension
        );
    }

    public byte[] encode(ForwardedPlayer player) throws VelocityForwardingException {
        requireOpen();
        Objects.requireNonNull(player, "player");
        VelocityForwardingVersion version = player.version();
        if (version.id() > config.maxSupportedVersion()) {
            throw new VelocityForwardingException(
                    ForwardingError.UNSUPPORTED_VERSION,
                    "Forwarding version exceeds the configured maximum"
            );
        }
        validateUsername(player.username());
        if (player.properties().size() > config.maxProperties()) {
            throw new VelocityForwardingException(
                    ForwardingError.TOO_MANY_PROPERTIES,
                    "Profile property count exceeds configured limit"
            );
        }
        validateExtension(version, player.extension());

        ForwardingWriter writer = new ForwardingWriter();
        writer.writeVarInt(version.id());
        writer.writeString(player.address().getHostAddress());
        writer.writeUuid(player.playerId());
        writer.writeString(player.username());
        writer.writeVarInt(player.properties().size());

        for (ProfileProperty property : player.properties()) {
            requireUtf8Length(property.name(), MAX_PROPERTY_NAME_BYTES, "property name");
            requireUtf8Length(
                    property.value(),
                    config.maxPropertyValueBytes(),
                    "property value"
            );
            writer.writeString(property.name());
            writer.writeString(property.value());
            writer.writeBoolean(property.signature().isPresent());
            if (property.signature().isPresent()) {
                String signature = property.signature().orElseThrow();
                requireUtf8Length(
                        signature,
                        config.maxPropertyValueBytes(),
                        "property signature"
                );
                writer.writeString(signature);
            }
        }
        writer.writeBytes(player.extension().bytes());

        byte[] payload = writer.toByteArray();
        if (payload.length + SIGNATURE_BYTES > config.maxPayloadBytes()) {
            throw new VelocityForwardingException(
                    ForwardingError.PAYLOAD_TOO_LARGE,
                    "Forwarding payload exceeds configured limit"
            );
        }

        byte[] signature = sign(payload);
        byte[] signed = new byte[signature.length + payload.length];
        System.arraycopy(signature, 0, signed, 0, signature.length);
        System.arraycopy(payload, 0, signed, signature.length, payload.length);
        return signed;
    }

    public byte[] encodeV1(ForwardedPlayer player) throws VelocityForwardingException {
        if (player.forwardingVersion() != VelocityForwardingVersion.MODERN_DEFAULT.id()) {
            throw new VelocityForwardingException(
                    ForwardingError.UNSUPPORTED_VERSION,
                    "encodeV1 only accepts forwarding version 1"
            );
        }
        return encode(player);
    }

    public boolean closed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        Arrays.fill(secret, (byte) 0);
        closed = true;
    }

    private static ForwardingExtension readExtension(
            VelocityForwardingVersion version,
            ForwardingReader reader
    ) throws VelocityForwardingException {
        if (version.hasPlatformExtension()) {
            byte[] bytes = reader.readRemainingBytes();
            if (bytes.length == 0) {
                throw new VelocityForwardingException(
                        ForwardingError.MALFORMED_PAYLOAD,
                        "Forwarding version " + version.id() + " requires profile-key data"
                );
            }
            return ForwardingExtension.of(bytes);
        }
        if (reader.remaining() != 0) {
            throw new VelocityForwardingException(
                    ForwardingError.TRAILING_DATA,
                    "Forwarding payload contains trailing data"
            );
        }
        return ForwardingExtension.empty();
    }

    private static void validateExtension(
            VelocityForwardingVersion version,
            ForwardingExtension extension
    ) throws VelocityForwardingException {
        if (version.hasPlatformExtension() && extension.isEmpty()) {
            throw new VelocityForwardingException(
                    ForwardingError.MALFORMED_PAYLOAD,
                    "Forwarding version " + version.id() + " requires profile-key data"
            );
        }
        if (!version.hasPlatformExtension() && !extension.isEmpty()) {
            throw new VelocityForwardingException(
                    ForwardingError.TRAILING_DATA,
                    "Forwarding version " + version.id() + " does not accept extension data"
            );
        }
    }

    private InetAddress readAddress(ForwardingReader reader)
            throws VelocityForwardingException {
        String address = reader.readString(MAX_ADDRESS_BYTES, "address");
        try {
            if (address.indexOf(':') >= 0) {
                return InetAddress.getByName(address);
            }
            String[] parts = address.split("\\.", -1);
            if (parts.length != 4) {
                throw new UnknownHostException("IPv4 address must contain four octets");
            }
            byte[] octets = new byte[4];
            for (int index = 0; index < parts.length; index++) {
                if (parts[index].isEmpty() || parts[index].length() > 3) {
                    throw new UnknownHostException("Invalid IPv4 octet");
                }
                int octet = Integer.parseInt(parts[index]);
                if (octet < 0 || octet > 255) {
                    throw new UnknownHostException("IPv4 octet is out of range");
                }
                octets[index] = (byte) octet;
            }
            return InetAddress.getByAddress(octets);
        } catch (UnknownHostException | NumberFormatException exception) {
            throw new VelocityForwardingException(
                    ForwardingError.INVALID_ADDRESS,
                    "Forwarded address is invalid",
                    exception
            );
        }
    }

    private static void validateUsername(String username)
            throws VelocityForwardingException {
        if (username.isEmpty() || username.length() > 16) {
            throw new VelocityForwardingException(
                    ForwardingError.INVALID_USERNAME,
                    "Forwarded username must contain between 1 and 16 characters"
            );
        }
        for (int index = 0; index < username.length(); index++) {
            char current = username.charAt(index);
            boolean valid = current == '_'
                    || current >= '0' && current <= '9'
                    || current >= 'A' && current <= 'Z'
                    || current >= 'a' && current <= 'z';
            if (!valid) {
                throw new VelocityForwardingException(
                        ForwardingError.INVALID_USERNAME,
                        "Forwarded username contains an invalid character"
                );
            }
        }
    }

    private static void requireUtf8Length(String value, int maxBytes, String field)
            throws VelocityForwardingException {
        if (value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new VelocityForwardingException(
                    ForwardingError.MALFORMED_PAYLOAD,
                    field + " length exceeds limit"
            );
        }
    }

    private synchronized byte[] sign(byte[] payload) throws VelocityForwardingException {
        requireOpen();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new VelocityForwardingException(
                    ForwardingError.MALFORMED_PAYLOAD,
                    "HMAC-SHA256 is unavailable",
                    exception
            );
        }
    }

    private void requireOpen() throws VelocityForwardingException {
        if (closed) {
            throw new VelocityForwardingException(
                    ForwardingError.FORWARDING_CLOSED,
                    "Velocity forwarding codec is closed"
            );
        }
    }
}
