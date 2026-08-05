# Native Velocity backend support

Helstrea treats Velocity as a proxy and implements the backend side of Velocity modern player forwarding. It does not make Velocity the owner of Helstrea actions or journals.

## Implemented milestones

The forwarding core implements protocol version 1 on the `velocity:player_info` login query channel:

- HMAC-SHA-256 verification over the forwarded payload
- player IP address
- authenticated UUID
- username
- signed and unsigned game profile properties
- strict size limits and fail-closed parsing
- one-shot login sessions with transaction-ID matching
- replay rejection after a matched response is consumed
- verified identity application through a platform adapter SPI
- a platform-neutral Paper configuration preflight and startup gate

The backend advertises version 1 in its login custom query. Velocity then returns a signed version 1 response. Newer forwarding versions are intentionally rejected until their public-key and session fields are implemented and tested.

## Forge and NeoForge integration contract

A Forge or NeoForge login adapter must:

1. Allocate a login transaction ID using the platform login-query mechanism.
2. Create a `VelocityForwardingSession` through `beginSession(transactionId)`.
3. Construct a `VelocityForwardingLoginCoordinator` with an adapter-specific `ForwardedIdentityApplier`.
4. Send `coordinator.challenge()` as the `velocity:player_info` login custom query.
5. Pass the actual response transaction ID and payload to `coordinator.acceptResponse(...)`.
6. Atomically replace the connection address and authenticated game profile inside the identity applier.
7. Reject null, malformed, unsigned, incorrectly signed, mismatched, repeated, or unapplicable responses.
8. Continue platform login work on the platform-required login or network thread.

The session is consumed before a matched response is decoded. A malformed response or identity-application failure therefore cannot reopen the same login session. The platform must disconnect rather than continue with an unverified or partially replaced identity.

## Paper integration contract

Paper already implements modern forwarding itself. Helstrea's Paper adapter must consume Paper's verified identity instead of injecting a second forwarding handshake.

`PaperVelocityPreflight.requireValid(...)` fails startup when any of these conditions are present:

- backend `server.properties` still has `online-mode=true`
- BungeeCord forwarding is enabled
- Paper Velocity forwarding is disabled
- forwarding secret is missing or does not match
- Paper's Velocity `online-mode` does not match the proxy's `online-mode`

The remaining Paper-specific work is reading these values from the actual versioned Paper configuration APIs and invoking the startup gate before Helstrea begins accepting players.

## Security boundary

Modern forwarding authenticates forwarded player information; it is not a firewall. Backends must only be reachable from trusted proxy addresses. The forwarding secret must not be committed, logged, or shared with Helstrea business-protocol keys.

## Remaining platform work

The public repository still needs the complete local server-core source before these hooks can be implemented safely:

- Forge 1.20.1 login custom-query injection
- NeoForge 1.21.1 login custom-query injection
- atomic platform implementations of `ForwardedIdentityApplier`
- Paper configuration snapshot reader and lifecycle wiring
- real Velocity-to-backend login tests
- Forge/FML 1.20.1 proxy handshake compatibility

The codec, one-shot session, login coordinator, identity application SPI, and Paper startup gate are the stable integration boundaries for those hooks.
