// Copyright AGNTCY Contributors (https://github.com/agntcy)
// SPDX-License-Identifier: Apache-2.0

package io.agntcy.slim.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.agntcy.slim.bindings.App;
import io.agntcy.slim.bindings.ClientConfig;
import io.agntcy.slim.bindings.ClientJwtAuth;
import io.agntcy.slim.bindings.IdentityProviderConfig;
import io.agntcy.slim.bindings.IdentityVerifierConfig;
import io.agntcy.slim.bindings.JwtAlgorithm;
import io.agntcy.slim.bindings.JwtAuth;
import io.agntcy.slim.bindings.JwtKeyConfig;
import io.agntcy.slim.bindings.JwtKeyData;
import io.agntcy.slim.bindings.JwtKeyFormat;
import io.agntcy.slim.bindings.JwtKeyType;
import io.agntcy.slim.bindings.Name;
import io.agntcy.slim.bindings.SlimBindings;
import io.agntcy.slim.bindings.SlimException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class SlimHelperTest {

    private static final String SECRET = "a-shared-secret-that-is-long-enough-for-slim";

    @BeforeAll
    static void init() {
        SlimHelper.initializeDefaults();
    }

    @Test
    void sharedSecretConfigsMatchCreateAppWithSecret() {
        Name name = new Name("org", "ns", "agent");

        var provider = assertInstanceOf(IdentityProviderConfig.SharedSecret.class,
                SlimHelper.sharedSecretProvider(name, SECRET));
        var verifier = assertInstanceOf(IdentityVerifierConfig.SharedSecret.class,
                SlimHelper.sharedSecretVerifier(name, SECRET));

        assertEquals(name.toString(), provider.id());
        assertEquals(SECRET, provider.data());
        assertEquals(name.toString(), verifier.id());
        assertEquals(SECRET, verifier.data());
    }

    @Test
    void sharedSecretConfigsAreAcceptedByBindings() throws Exception {
        Name name = new Name("org", "ns", "shared-secret-app");
        App app = SlimBindings.getGlobalService().createApp(name,
                SlimHelper.sharedSecretProvider(name, SECRET),
                SlimHelper.sharedSecretVerifier(name, SECRET));
        assertNotNull(app);
    }

    @Test
    void jwtEdDsaWithJwksConfigsAreAcceptedByBindings(@TempDir Path dir) throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String kid = "did:key:test-agent";

        Path privateKey = dir.resolve("agent.pem");
        Files.writeString(privateKey, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");

        // An X.509 Ed25519 public key ends with the 32-byte raw key.
        byte[] spki = keyPair.getPublic().getEncoded();
        byte[] raw = Arrays.copyOfRange(spki, spki.length - 32, spki.length);
        Path jwks = dir.resolve("trusted.jwks.json");
        Files.writeString(jwks, """
                {"keys":[{"kty":"OKP","crv":"Ed25519","alg":"EdDSA","kid":"%s","x":"%s"}]}
                """.formatted(kid, Base64.getUrlEncoder().withoutPadding().encodeToString(raw)));

        Name name = new Name("org", "ns", "jwt-app");
        IdentityProviderConfig provider = new IdentityProviderConfig.Jwt(new ClientJwtAuth(
                new JwtKeyType.Encoding(new JwtKeyConfig(JwtAlgorithm.ED_DSA, JwtKeyFormat.PEM,
                        new JwtKeyData.File(privateKey.toString()))),
                List.of("slim"), "test-issuer", kid, Duration.ofMinutes(10)));
        IdentityVerifierConfig verifier = new IdentityVerifierConfig.Jwt(new JwtAuth(
                new JwtKeyType.Decoding(new JwtKeyConfig(JwtAlgorithm.ED_DSA, JwtKeyFormat.JWKS,
                        new JwtKeyData.File(jwks.toString()))),
                List.of("slim"), "test-issuer", null, Duration.ofMinutes(10)));

        App app = SlimBindings.getGlobalService().createApp(name, provider, verifier);
        assertNotNull(app);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void createServerUsesSuppliedIdentityBeforeConnecting(@TempDir Path dir) throws Exception {
        // A provider with an invalid signing key must fail at app creation,
        // proving the supplied identity is used and no node connection is needed.
        Path badKey = Files.writeString(dir.resolve("bad.pem"), "not a key");
        IdentityProviderConfig provider = new IdentityProviderConfig.Jwt(new ClientJwtAuth(
                new JwtKeyType.Encoding(new JwtKeyConfig(JwtAlgorithm.ED_DSA, JwtKeyFormat.PEM,
                        new JwtKeyData.File(badKey.toString()))),
                List.of("slim"), "test-issuer", "did:key:missing", Duration.ofMinutes(10)));
        Name name = new Name("org", "ns", "server");
        ClientConfig clientConfig = SlimBindings.newSecureClientConfig("127.0.0.1:1");

        assertThrows(SlimException.class, () -> SlimHelper.createServer(name, provider,
                SlimHelper.sharedSecretVerifier(name, SECRET), clientConfig));
        assertThrows(SlimException.class, () -> SlimHelper.createChannel(name,
                new Name("org", "ns", "remote"), provider,
                SlimHelper.sharedSecretVerifier(name, SECRET), clientConfig));
    }

    @Test
    void rejectsNullArguments() {
        Name name = new Name("org", "ns", "agent");
        var provider = SlimHelper.sharedSecretProvider(name, SECRET);
        var verifier = SlimHelper.sharedSecretVerifier(name, SECRET);
        var clientConfig = SlimBindings.newInsecureClientConfig("127.0.0.1:1");

        assertThrows(NullPointerException.class,
                () -> SlimHelper.createServer(null, provider, verifier, clientConfig));
        assertThrows(NullPointerException.class,
                () -> SlimHelper.createServer(name, null, verifier, clientConfig));
        assertThrows(NullPointerException.class,
                () -> SlimHelper.createServer(name, provider, null, clientConfig));
        assertThrows(NullPointerException.class,
                () -> SlimHelper.createServer(name, provider, verifier, null));
        assertThrows(NullPointerException.class,
                () -> SlimHelper.createChannel(name, null, provider, verifier, clientConfig));
    }
}
