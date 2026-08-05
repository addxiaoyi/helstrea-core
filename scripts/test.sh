#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/build/self-test"
rm -rf "$OUT"
mkdir -p "$OUT/main" "$OUT/test"

find "$ROOT/src/main/java" -name '*.java' -print0 \
  | xargs -0 javac --release 17 -Xlint:all -Werror -d "$OUT/main"

find "$ROOT/src/test/java" -name '*.java' -print0 \
  | xargs -0 javac --release 17 -Xlint:all -Werror \
      -cp "$OUT/main" -d "$OUT/test"

java -ea -cp "$OUT/main:$OUT/test" \
  io.helstrea.velocity.forwarding.VelocityForwardingSelfTest
java -ea -cp "$OUT/main:$OUT/test" \
  io.helstrea.velocity.forwarding.VelocityForwardingSessionSelfTest
java -ea -cp "$OUT/main:$OUT/test" \
  io.helstrea.velocity.forwarding.VelocityForwardingLoginCoordinatorSelfTest
java -ea -cp "$OUT/main:$OUT/test" \
  io.helstrea.velocity.forwarding.VelocityForwardingBackendSelfTest
java -ea -cp "$OUT/main:$OUT/test" \
  io.helstrea.velocity.forwarding.paper.PaperVelocityPreflightSelfTest
java -ea -cp "$OUT/main:$OUT/test" \
  io.helstrea.velocity.forwarding.security.ForwardingSecretLoaderSelfTest
java -ea -cp "$OUT/main:$OUT/test" \
  io.helstrea.velocity.forwarding.security.TrustedProxyPolicySelfTest
