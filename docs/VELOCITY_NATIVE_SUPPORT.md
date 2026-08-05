# Native Velocity backend support

Helstrea treats Velocity as a proxy and implements the backend side of Velocity modern player forwarding. It does not make Velocity the owner of Helstrea actions or journals.

## Scope of the first milestone

The current forwarding core implements protocol version 1 on the `velocity:player_info` login query channel:

- HMAC-SHA-256 verification over the forwarded payload
- player IP address
- authenticated UUID
- username
- signed and unsigned game profile properties
- strict size limits and fail-closed parsing
- a platform-neutral login challenge/response API

The backend advertises version 1 in its login custom query. Velocity then returns a signed version 1 response. Newer forwarding versions are intentionally rejected until their public-key and session fields are implemented and tested.

## Platform integration contract

A Forge or NeoForge login adapter must:

1. Create a random login transaction ID.
2. Send `VelocityForwardingLoginSupport.createChallenge(transactionId)` as a login custom query.
3. Match the response transaction ID.
4. Call `acceptResponse(responseBytes)` before accepting the player.
5. Replace the connection address and authenticated game profile with the verified values.
6. Reject a null, malformed, unsigned, or incorrectly signed response when `requireProxy` is enabled.
7. Continue platform login work on the platform-required login or network thread.

Paper already implements modern forwarding itself. Helstrea's Paper adapter should validate configuration and consume Paper's verified identity rather than injecting a second forwarding handshake.

## Security boundary

Modern forwarding authenticates forwarded player information; it is not a firewall. Backends must only be reachable from trusted proxy addresses. The forwarding secret must not be committed, logged, or shared with Helstrea business-protocol keys.

## Remaining platform work

This public repository still needs the actual server-core source before the following hooks can be implemented safely:

- Forge 1.20.1 login custom-query injection
- NeoForge 1.21.1 login custom-query injection
- connection address and game-profile replacement
- Paper configuration preflight
- real Velocity-to-backend login tests

The shared codec and login service are the stable integration boundary for those hooks.
