// Copyright AGNTCY Contributors (https://github.com/agntcy)
// SPDX-License-Identifier: Apache-2.0

package io.agntcy.slim.a2a;

import java.util.Objects;

import io.agntcy.slim.bindings.App;
import io.agntcy.slim.bindings.ClientConfig;
import io.agntcy.slim.bindings.IdentityProviderConfig;
import io.agntcy.slim.bindings.IdentityVerifierConfig;
import io.agntcy.slim.bindings.Name;
import io.agntcy.slim.bindings.Service;
import io.agntcy.slim.bindings.SlimBindings;
import io.agntcy.slim.bindings.slimrpc.Channel;
import io.agntcy.slim.bindings.slimrpc.Server;

/**
 * Convenience helpers for bootstrapping SLIM RPC server and client instances.
 *
 * <p>The general overloads take the SLIM app identity as an
 * {@link IdentityProviderConfig} / {@link IdentityVerifierConfig} pair (shared
 * secret, JWT, static JWT or SPIRE) and the node connection as a
 * {@link ClientConfig} (plain, TLS, mTLS, with optional basic/JWT auth). The
 * {@code secret}/{@code serverAddr} overloads are shortcuts for a shared-secret
 * identity over an insecure connection.
 */
public final class SlimHelper {

    static {
        NativeLibraryLoader.ensureExtracted();
    }

    private SlimHelper() {}

    /**
     * Bootstraps a SLIM RPC server with a caller-supplied identity and node connection.
     *
     * @param localName        the SLIM name this server registers under
     * @param identityProvider how this app proves its identity to peers
     * @param identityVerifier how this app verifies the identity of peers
     * @param clientConfig     how to connect to the SLIM node (endpoint, TLS, auth)
     * @return a ready-to-register {@link Server}
     */
    public static Server createServer(Name localName,
                                      IdentityProviderConfig identityProvider,
                                      IdentityVerifierConfig identityVerifier,
                                      ClientConfig clientConfig) throws Exception {
        Objects.requireNonNull(localName, "localName");
        ConnectedApp connected = connect(localName, identityProvider, identityVerifier, clientConfig);
        return Server.newWithConnection(connected.app(), localName, connected.connId());
    }

    /**
     * Bootstraps a SLIM RPC server using a shared secret over an insecure connection.
     *
     * @param instanceName unique instance name for this server (e.g. "server")
     * @param org          the SLIM name org component
     * @param ns           the SLIM name namespace component
     * @param secret       shared secret for authentication
     * @param serverAddr   the SLIM gateway address (e.g. "localhost:9090")
     * @return a ready-to-register {@link Server}
     */
    public static Server createServer(String instanceName, String org, String ns,
                                      String secret, String serverAddr) throws Exception {
        Name localName = new Name(org, ns, instanceName);
        return createServer(localName,
                sharedSecretProvider(localName, secret),
                sharedSecretVerifier(localName, secret),
                SlimBindings.newInsecureClientConfig(serverAddr));
    }

    /**
     * Bootstraps a SLIM RPC channel (client) with a caller-supplied identity and node connection.
     *
     * @param localName        the SLIM name of this client
     * @param remoteName       the SLIM name of the remote server
     * @param identityProvider how this app proves its identity to peers
     * @param identityVerifier how this app verifies the identity of peers
     * @param clientConfig     how to connect to the SLIM node (endpoint, TLS, auth)
     * @return a connected {@link Channel}
     */
    public static Channel createChannel(Name localName, Name remoteName,
                                        IdentityProviderConfig identityProvider,
                                        IdentityVerifierConfig identityVerifier,
                                        ClientConfig clientConfig) throws Exception {
        Objects.requireNonNull(localName, "localName");
        Objects.requireNonNull(remoteName, "remoteName");
        ConnectedApp connected = connect(localName, identityProvider, identityVerifier, clientConfig);
        return Channel.newWithConnection(connected.app(), remoteName, connected.connId());
    }

    /**
     * Bootstraps a SLIM RPC channel (client) using a shared secret over an insecure connection.
     *
     * @param instanceName unique instance name for this client (e.g. "client")
     * @param org          the SLIM name org component
     * @param ns           the SLIM name namespace component
     * @param remoteName   instance name of the remote server (e.g. "server")
     * @param secret       shared secret for authentication
     * @param serverAddr   the SLIM gateway address (e.g. "localhost:9090")
     * @return a connected {@link Channel}
     */
    public static Channel createChannel(String instanceName, String org, String ns,
                                        String remoteName, String secret, String serverAddr) throws Exception {
        Name localName = new Name(org, ns, instanceName);
        return createChannel(localName, new Name(org, ns, remoteName),
                sharedSecretProvider(localName, secret),
                sharedSecretVerifier(localName, secret),
                SlimBindings.newInsecureClientConfig(serverAddr));
    }

    /**
     * Shared-secret identity provider for {@code name}, equivalent to what
     * {@code Service.createAppWithSecret} uses.
     */
    public static IdentityProviderConfig sharedSecretProvider(Name name, String secret) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(secret, "secret");
        return new IdentityProviderConfig.SharedSecret(name.toString(), secret);
    }

    /**
     * Shared-secret identity verifier for {@code name}, equivalent to what
     * {@code Service.createAppWithSecret} uses.
     */
    public static IdentityVerifierConfig sharedSecretVerifier(Name name, String secret) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(secret, "secret");
        return new IdentityVerifierConfig.SharedSecret(name.toString(), secret);
    }

    /**
     * Initializes the SLIM runtime with default configuration.
     * Must be called once before creating servers or channels.
     */
    public static void initializeDefaults() {
        SlimBindings.initializeWithDefaults();
    }

    private record ConnectedApp(App app, long connId) {}

    private static ConnectedApp connect(Name localName,
                                        IdentityProviderConfig identityProvider,
                                        IdentityVerifierConfig identityVerifier,
                                        ClientConfig clientConfig) throws Exception {
        Objects.requireNonNull(identityProvider, "identityProvider");
        Objects.requireNonNull(identityVerifier, "identityVerifier");
        Objects.requireNonNull(clientConfig, "clientConfig");
        Service service = SlimBindings.getGlobalService();
        App app = service.createApp(localName, identityProvider, identityVerifier);
        long connId = service.connect(clientConfig);
        app.subscribe(app.name(), connId);
        return new ConnectedApp(app, connId);
    }
}
