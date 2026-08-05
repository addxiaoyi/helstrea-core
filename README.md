# Helstrea Core

Helstrea is a multi-platform Minecraft server core. This branch contains the server-side foundation for native Velocity proxy support without making Velocity the owner of Helstrea actions or journals.

## Build and test

Requires Java 17 or newer:

```bash
bash scripts/test.sh
```

A Gradle `check` task is also configured for integration with the complete local Helstrea project.

## Implemented

- `velocity:player_info` negotiation for modern forwarding versions 1 through 4
- HMAC-SHA-256 verification before parsing
- strict UUID, IP address, username and profile-property decoding
- authenticated v2/v3 profile-key extension preservation
- one-shot login sessions with transaction matching, replay rejection and deadlines
- atomic forwarded-identity application SPI
- secure `VelocityForwardingBackend` integration facade
- Velocity-compatible UTF-8 secret-file loading
- trusted proxy IPv4/IPv6 CIDR gate
- forwarding secret clearing and login rejection on shutdown
- Paper Velocity configuration preflight and startup gate
- deterministic self-tests and GitHub Actions CI

## Remaining integration

The public repository does not yet contain the complete local server-core source. These changes must be applied to that source rather than guessed in isolation:

- Forge 1.20.1 login custom-query hook
- NeoForge 1.21.1 login custom-query hook
- platform implementations of connection-address and authenticated-profile replacement
- Paper configuration snapshot reader and lifecycle wiring
- real Velocity-to-backend integration tests
- Forge/FML 1.20.1 proxy handshake compatibility

See [Native Velocity backend support](docs/VELOCITY_NATIVE_SUPPORT.md).
