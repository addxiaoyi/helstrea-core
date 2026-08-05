package io.helstrea.velocity.forwarding.security;

import io.helstrea.velocity.forwarding.ForwardingError;
import io.helstrea.velocity.forwarding.VelocityForwardingException;
import java.io.IOException;
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
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            byte[] bytes = String.join("", lines).getBytes(StandardCharsets.UTF_8);
            if (bytes.length == 0) {
                throw new VelocityForwardingException(
                        ForwardingError.EMPTY_SECRET,
                        "Velocity forwarding secret file is empty"
                );
            }
            return bytes;
        } catch (IOException exception) {
            throw new VelocityForwardingException(
                    ForwardingError.SECRET_FILE_INVALID,
                    "Unable to read a valid UTF-8 Velocity forwarding secret file: " + path,
                    exception
            );
        }
    }
}
