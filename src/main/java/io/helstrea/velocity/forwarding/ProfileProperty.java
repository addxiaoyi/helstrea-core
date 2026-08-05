package io.helstrea.velocity.forwarding;

import java.util.Objects;
import java.util.Optional;

public record ProfileProperty(String name, String value, Optional<String> signature) {
    public ProfileProperty {
        name = Objects.requireNonNull(name, "name");
        value = Objects.requireNonNull(value, "value");
        signature = Objects.requireNonNull(signature, "signature");
    }

    public static ProfileProperty unsigned(String name, String value) {
        return new ProfileProperty(name, value, Optional.empty());
    }

    public static ProfileProperty signed(String name, String value, String signature) {
        return new ProfileProperty(name, value, Optional.of(signature));
    }
}
