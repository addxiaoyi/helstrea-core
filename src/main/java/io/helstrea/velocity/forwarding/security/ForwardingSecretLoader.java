package io.helstrea.velocity.forwarding.security;

import io.helstrea.velocity.forwarding.ForwardingError;
import io.helstrea.velocity.forwarding.VelocityForwardingException;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ForwardingSecretLoader {
    public static final long MAX_SECRET_FILE_BYTES = 4_096;

    private ForwardingSecretLoader() {
    }

    public static byte[] load(Path path) throws VelocityForwardingException {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            throw new VelocityForwardingException(
                    ForwardingError.SECRET_FILE_MISSING,
                    "Velocity forwarding secret file does not exist: " + path
            );
        }
        if (!Files.isRegularFile(path)) {
            throw new VelocityForwardingException(
                    ForwardingError.SECRET_FILE_NOT_REGULAR,
                    "Velocity forwarding secret path is not a regular file: " + path
            );
        }

        try {
            long size = Files.size(path);
            if (size > MAX_SECRET_FILE_BYTES) {
                throw new VelocityForwardingException(
                        ForwardingError.SECRET_FILE_TOO_LARGE,
                        "Velocity forwarding secret file exceeds "
                                + MAX_SECRET_FILE_BYTES
                                + " bytes"
                );
            }
            byte[] raw = Files.readAllBytes(path);
            String decoded = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(raw))
                    .toString();
            List<String> lines = decoded.lines().toList();
            String secret = String.join("", lines);
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length == 0) {
                throw new VelocityForwardingException(
                        ForwardingError.EMPTY_SECRET,
                        "Velocity forwarding secret file is empty"
                );
            }
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new VelocityForwardingException(
                    ForwardingError.SECRET_FILE_INVALID,
                    "Velocity forwarding secret file is not valid UTF-8",
                    exception
            );
        } catch (IOException exception) {
            throw new VelocityForwardingException(
                    ForwardingError.SECRET_FILE_INVALID,
                    "Unable to read Velocity forwarding secret file: " + path,
                    exception
            );
        }
    }
}
