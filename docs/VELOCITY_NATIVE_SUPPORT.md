# Native Velocity backend support

Helstrea treats Velocity as a proxy and implements the backend side of Velocity modern player forwarding. Velocity does not own Helstrea actions, journals, or world state.

## Implemented milestones

The forwarding core implements `velocity:player_info` modern forwarding versions 1 through 4:

- HMAC-SHA-256 verification before payload parsing
- player IP address, authenticated UUID, username, and profile properties
- version 1 (`MODERN_DEFAULT`) decoding
- versions 2 and 3 authenticated profile-key extension preservation
- version 4 (`MODERN_LAZY_SESSION`) decoding for modern Minecraft servers
- configurable maximum advertised forwarding version
- strict payload, property, string, and extension limits
- one-shot login sessions with transaction-ID matching
- replay rejection after a matched response is consumed
- configurable monotonic response deadline
- verified identity application through a platform adapter SPI
- trusted proxy IPv4 and IPv6 CIDR enforcement before a challenge is sent
- Velocity-compatible UTF-8 forwarding secret loading
- secret erasure and new-login rejection during shutdown
- a platform-neutral Paper configuration preflight and startup gate

The secure default advertises version 4 and uses a 10-second response deadline. A deployment can select forwarding version 1 through 4 and a response timeout from 100 to 60000 milliseconds. A response above the advertised version or after the deadline is rejected.

Versions 2 and 3 contain Minecraft-version-specific profile public-key structures. The shared core preserves those HMAC-authenticated bytes as an immutable `ForwardingExtension`; the matching platform adapter must decode and validate them using that Minecraft version's native key types. Versions 1 and 4 reject any trailing extension data.

## Forge and NeoForge integration contract

A Forge or NeoForge adapter should construct one `VelocityForwardingBackend` during module startup:

```java
VelocityForwardingBackend backend = VelocityForwardingBackend.create(
        secretPath,
        trustedProxyCidrs,
        forwardingConfig
);
```

For each login it must:

1. Read the actual remote socket address before any forwarded identity is applied.
2. Call `backend.beginLogin(remoteAddress, transactionId, identityApplier)`.
3. Send `coordinator.challenge()` through the platform login-query mechanism.
4. Pass the actual response transaction ID and bytes to `coordinator.acceptResponse(...)`.
5. Atomically replace the connection address and authenticated game profile through `ForwardedIdentityApplier`.
6. For versions 2 or 3, decode and validate the preserved key extension before accepting login.
7. Reject null, malformed, unsigned, incorrectly signed, mismatched, repeated, expired, untrusted, or unapplicable responses.
8. Continue platform login work on the platform-required login or network thread.

The session is consumed before a matched response is decoded. A malformed, expired, or unapplicable response cannot reopen the same login session. The platform must disconnect instead of continuing with an unverified or partially replaced identity.

Forge 1.20.1 and NeoForge 1.21.1 target modern forwarding version 4, whose forwarded identity layout does not append profile-key bytes.

## Paper integration contract

Paper already implements modern forwarding. Helstrea's Paper adapter must consume Paper's verified identity instead of injecting a second forwarding handshake.

`PaperVelocityPreflight.requireValid(...)` fails startup when any of these conditions are present:

- backend `server.properties` has `online-mode=true`
- BungeeCord forwarding is enabled
- Paper Velocity forwarding is disabled
- forwarding secret is missing or does not match
- Paper's Velocity `online-mode` does not match the proxy's `online-mode`

The remaining Paper work is reading these values from the actual versioned Paper configuration APIs and invoking the startup gate before Helstrea accepts players.

## Secret-file compatibility

Velocity reads its forwarding secret as UTF-8, removes line separators, and concatenates all lines without trimming other characters. `ForwardingSecretLoader` reproduces that behavior and additionally fails closed for:

- missing or non-regular files
- invalid UTF-8
- empty secrets
- files larger than 4096 bytes

The same secret file can be mounted into Velocity and the Helstrea backend without newline-dependent HMAC mismatches. Platform code should prefer `VelocityForwardingBackend.create(...)` over creating a codec from an immutable Java `String`.

## Network boundary

Modern forwarding authenticates player information; it is not a firewall.

`VelocityForwardingBackend` checks the remote connection address against a non-empty `TrustedProxyPolicy` before generating a login challenge.

Example allowlist:

```text
127.0.0.1/32
10.20.0.0/16
2001:db8:42::/48
```

Hostnames, scoped IPv6 addresses, invalid prefixes, and an empty allowlist are rejected. Network firewalls must still restrict the backend port to the proxy network.

## Lifecycle contract

Startup order:

```text
load config
→ validate trusted proxy CIDRs
→ load forwarding secret
→ create VelocityForwardingBackend
→ register platform login hooks
→ accept players
```

Shutdown order:

```text
stop accepting new logins
→ unregister or disable platform login hooks
→ close VelocityForwardingBackend
→ release remaining platform resources
```

`close()` is idempotent. It clears the codec's in-memory secret, rejects new sessions, and causes pending responses that have not yet been authenticated to fail with `FORWARDING_CLOSED`.

## Remaining platform work

The public repository still needs the complete local server-core source before these hooks can be implemented safely:

- Forge 1.20.1 login custom-query injection
- NeoForge 1.21.1 login custom-query injection
- atomic platform implementations of `ForwardedIdentityApplier`
- Paper configuration snapshot reader and lifecycle wiring
- real Velocity-to-backend login tests
- Forge/FML 1.20.1 proxy handshake compatibility

The backend facade, codec, source-address gate, one-shot session, identity SPI, and Paper startup gate are stable integration boundaries for those hooks.
