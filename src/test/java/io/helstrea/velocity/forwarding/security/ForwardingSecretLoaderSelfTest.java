package io.helstrea.velocity.forwarding.security;

import io.helstrea.velocity.forwarding.ForwardingError;
import io.helstrea.velocity.forwarding.VelocityForwardingException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class ForwardingSecretLoaderSelfTest {
    private ForwardingSecretLoaderSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("helstrea-forwarding-secret-");
        try {
            loadsWithVelocityLineSemantics(directory);
            preservesNonLineWhitespace(directory);
            rejectsMissingFile(directory);
            rejectsEmptyFile(directory);
            rejectsInvalidUtf8(directory);
            rejectsOversizedFile(directory);
            System.out.println("Velocity forwarding secret loader self-test: PASS");
        } finally {
            deleteTree(directory);
        }
    }

    private static void loadsWithVelocityLineSemantics(Path directory) throws Exception {
        Path path = directory.resolve("multiline.secret");
        Files.writeString(path, "alpha\nbeta\r\ngamma\n", StandardCharsets.UTF_8);
        byte[] actual = ForwardingSecretLoader.load(path);
        check(
                "alphabetagamma".equals(new String(actual, StandardCharsets.UTF_8)),
                "secret loader must concatenate lines exactly like Velocity"
        );
    }

    private static void preservesNonLineWhitespace(Path directory) throws Exception {
        Path path = directory.resolve("spaces.secret");
        Files.writeString(path, "  secret value  ", StandardCharsets.UTF_8);
        byte[] actual = ForwardingSecretLoader.load(path);
        check(
                "  secret value  ".equals(new String(actual, StandardCharsets.UTF_8)),
                "secret loader must not trim spaces"
        );
    }

    private static void rejectsMissingFile(Path directory) throws Exception {
        expect(
                ForwardingError.SECRET_FILE_MISSING,
                () -> ForwardingSecretLoader.load(directory.resolve("missing.secret"))
        );
    }

    private static void rejectsEmptyFile(Path directory) throws Exception {
        Path path = directory.resolve("empty.secret");
        Files.write(path, new byte[0]);
        expect(ForwardingError.EMPTY_SECRET, () -> ForwardingSecretLoader.load(path));
    }

    private static void rejectsInvalidUtf8(Path directory) throws Exception {
        Path path = directory.resolve("invalid.secret");
        Files.write(path, new byte[] {(byte) 0xc3, 0x28});
        expect(
                ForwardingError.SECRET_FILE_INVALID,
                () -> ForwardingSecretLoader.load(path)
        );
    }

    private static void rejectsOversizedFile(Path directory) throws Exception {
        Path path = directory.resolve("large.secret");
        Files.write(path, new byte[(int) ForwardingSecretLoader.MAX_SECRET_FILE_BYTES + 1]);
        expect(
                ForwardingError.SECRET_FILE_TOO_LARGE,
                () -> ForwardingSecretLoader.load(path)
        );
    }

    private static void expect(ForwardingError error, ThrowingAction action)
            throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected forwarding error " + error);
        } catch (VelocityForwardingException exception) {
            check(
                    exception.error() == error,
                    "expected " + error + " but received " + exception.error()
            );
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            try {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                });
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
