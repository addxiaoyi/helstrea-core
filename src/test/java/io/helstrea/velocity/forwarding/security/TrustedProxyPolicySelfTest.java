package io.helstrea.velocity.forwarding.security;

import io.helstrea.velocity.forwarding.ForwardingError;
import io.helstrea.velocity.forwarding.VelocityForwardingCodec;
import io.helstrea.velocity.forwarding.VelocityForwardingConfig;
import io.helstrea.velocity.forwarding.VelocityForwardingException;
import io.helstrea.velocity.forwarding.VelocityForwardingLoginSupport;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class TrustedProxyPolicySelfTest {
    private TrustedProxyPolicySelfTest() {
    }

    public static void main(String[] args) throws Exception {
        acceptsIPv4AndIPv6Networks();
        rejectsUntrustedSource();
        rejectsInvalidCidrs();
        rejectsEmptyPolicy();
        endpointChecksSourceBeforeChallenge();
        System.out.println("Velocity trusted proxy policy self-test: PASS");
    }

    private static void acceptsIPv4AndIPv6Networks() throws Exception {
        TrustedProxyPolicy policy = policy();
        check(
                policy.isTrusted(InetAddress.getByName("127.0.0.1")),
                "loopback proxy must be trusted"
        );
        check(
                policy.isTrusted(InetAddress.getByName("10.42.7.9")),
                "IPv4 address inside configured CIDR must be trusted"
        );
        check(
                policy.isTrusted(InetAddress.getByName("2001:db8:10::42")),
                "IPv6 address inside configured CIDR must be trusted"
        );
        check(
                !policy.isTrusted(InetAddress.getByName("11.0.0.1")),
                "IPv4 address outside configured CIDRs must not be trusted"
        );
    }

    private static void rejectsUntrustedSource() throws Exception {
        expect(
                ForwardingError.UNTRUSTED_PROXY,
                () -> policy().requireTrusted(InetAddress.getByName("192.0.2.10"))
        );
    }

    private static void rejectsInvalidCidrs() throws Exception {
        expect(
                ForwardingError.INVALID_PROXY_CIDR,
                () -> TrustedProxyPolicy.fromCidrs(List.of("10.0.0.0/33"))
        );
        expect(
                ForwardingError.INVALID_PROXY_CIDR,
                () -> TrustedProxyPolicy.fromCidrs(List.of("proxy.example.com/32"))
        );
        expect(
                ForwardingError.INVALID_PROXY_CIDR,
                () -> TrustedProxyPolicy.fromCidrs(List.of("fe80::1%eth0/64"))
        );
    }

    private static void rejectsEmptyPolicy() throws Exception {
        expect(
                ForwardingError.EMPTY_TRUSTED_PROXY_SET,
                () -> TrustedProxyPolicy.fromCidrs(List.of())
        );
    }

    private static void endpointChecksSourceBeforeChallenge() throws Exception {
        VelocityForwardingConfig config = VelocityForwardingConfig.secureDefaults();
        VelocityForwardingCodec codec = new VelocityForwardingCodec(
                "endpoint secret".getBytes(StandardCharsets.UTF_8),
                config
        );
        VelocityForwardingEndpoint endpoint = new VelocityForwardingEndpoint(
                new VelocityForwardingLoginSupport(codec, config),
                policy()
        );
        var session = endpoint.beginSession(InetAddress.getByName("10.0.0.8"), 71);
        check(
                session.challenge().transactionId() == 71,
                "trusted source must receive a login challenge"
        );
        expect(
                ForwardingError.UNTRUSTED_PROXY,
                () -> endpoint.beginSession(InetAddress.getByName("203.0.113.7"), 72)
        );
    }

    private static TrustedProxyPolicy policy() throws Exception {
        return TrustedProxyPolicy.fromCidrs(List.of(
                "127.0.0.1/32",
                "10.0.0.0/8",
                "2001:db8::/32"
        ));
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
