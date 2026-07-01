// Copyright AGNTCY Contributors (https://github.com/agntcy)
// SPDX-License-Identifier: Apache-2.0

package io.agntcy.slim.a2a;

import io.agntcy.slim.bindings.App;
import io.agntcy.slim.bindings.Channel;
import io.agntcy.slim.bindings.ClientConfig;
import io.agntcy.slim.bindings.Name;
import io.agntcy.slim.bindings.Server;
import io.agntcy.slim.bindings.Service;
import io.agntcy.slim.bindings.SlimBindings;

/**
 * Convenience helpers for bootstrapping SLIM RPC server and client instances.
 */
public final class SlimHelper {

    static {
        NativeLibraryLoader.ensureExtracted();
    }

    private SlimHelper() {}

    /**
     * Bootstraps a SLIM RPC server.
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
        Service service = SlimBindings.getGlobalService();
        Name localName = new Name(org, ns, instanceName);
        App app = service.createAppWithSecret(localName, secret);
        ClientConfig clientConfig = SlimBindings.newInsecureClientConfig(serverAddr);
        long connId = service.connect(clientConfig);
        app.subscribe(app.name(), connId);
        return Server.newWithConnection(app, localName, connId);
    }

    /**
     * Bootstraps a SLIM RPC channel (client).
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
        Service service = SlimBindings.getGlobalService();
        Name localName = new Name(org, ns, instanceName);
        Name remote = new Name(org, ns, remoteName);
        App app = service.createAppWithSecret(localName, secret);
        ClientConfig clientConfig = SlimBindings.newInsecureClientConfig(serverAddr);
        long connId = service.connect(clientConfig);
        app.subscribe(app.name(), connId);
        return Channel.newWithConnection(app, remote, connId);
    }

    /**
     * Initializes the SLIM runtime with default configuration.
     * Must be called once before creating servers or channels.
     */
    public static void initializeDefaults() {
        SlimBindings.initializeWithDefaults();
    }
}
