# Helstrea Core

Helstrea is a multi-platform Minecraft server core. This branch contains the first native Velocity backend-support milestone: a dependency-free, fail-closed implementation of Velocity modern player forwarding protocol version 1.

## Build and test

Requires Java 17 or newer:

```bash
bash scripts/test.sh
```

A Gradle `check` task is also configured for integration with the complete local Helstrea project.

## Implemented

- `velocity:player_info` version negotiation for forwarding v1
- HMAC-SHA-256 payload verification
- strict UUID, address, username and profile-property decoding
- platform-neutral login challenge/response service
- deterministic self-test and GitHub Actions gate

## Remaining integration

The public repository does not yet contain the complete local server-core source. The following work must be applied to that source rather than guessed in isolation:

- Forge 1.20.1 login custom-query hook
- NeoForge 1.21.1 login custom-query hook
- connection address and authenticated profile replacement
- Paper configuration preflight
- real Velocity-to-backend integration tests

See [Native Velocity backend support](docs/VELOCITY_NATIVE_SUPPORT.md).
