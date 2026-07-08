// Copyright AGNTCY Contributors (https://github.com/agntcy)
// SPDX-License-Identifier: Apache-2.0

package io.agntcy.slim.a2a.examples.echo;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import io.agntcy.slim.a2a.SlimA2AClient;
import io.agntcy.slim.a2a.SlimHelper;
import io.agntcy.slim.bindings.Channel;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Echo agent client entry point.
 *
 * Usage:
 *   task echo-client -- -Dexec.args="--server localhost:9090"
 */
public final class ClientMain {

    public static void main(String[] args) throws Exception {
        String serverAddr = ServerMain.DEFAULT_SERVER_ADDR;
        String remoteInstance = "server";
        for (int i = 0; i < args.length; i++) {
            if ("--server".equals(args[i]) && i + 1 < args.length) {
                serverAddr = args[i + 1];
            } else if ("--secret".equals(args[i]) && i + 1 < args.length) {
                ServerMain.SHARED_SECRET = args[i + 1];
            } else if ("--org".equals(args[i]) && i + 1 < args.length) {
                ServerMain.ORG = args[i + 1];
            } else if ("--ns".equals(args[i]) && i + 1 < args.length) {
                ServerMain.NS = args[i + 1];
            } else if ("--remote-instance".equals(args[i]) && i + 1 < args.length) {
                remoteInstance = args[i + 1];
            }
        }

        SlimHelper.initializeDefaults();

        Channel channel = SlimHelper.createChannel(
                "client", ServerMain.ORG, ServerMain.NS,
                remoteInstance, ServerMain.SHARED_SECRET, serverAddr);

        try {
            var client = new SlimA2AClient(channel);

            sendTestMessage(client, "Hello from Java A2A client!");
            sendTestMessage(client, "/shout Hello from Java A2A client!");
            sendTestMessage(client, "/reverse Hello from Java A2A client!");

            // getExtendedAgentCard() is intentionally not called here: it hangs and, worse,
            // leaves the server's session/participant capacity stuck for every other client
            // until restarted. Re-enable once that server-side bug is fixed.
            System.out.println("SLIM_A2A_CLIENT_DONE");
        } finally {
            channel.close(Duration.ofSeconds(5));
        }
    }

    private static void sendTestMessage(SlimA2AClient client, String text) throws Exception {
        String contextId = UUID.randomUUID().toString();
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .contextId(contextId)
                .parts(List.of(new TextPart(text, null)))
                .build();

        MessageSendParams params = MessageSendParams.builder()
                .message(message)
                .build();

        System.out.println("Sending: \"" + text + "\"");
        EventKind result = client.sendMessage(params);

        if (result instanceof Task task) {
            System.out.println("Got Task: id=" + task.id()
                    + " state=" + task.status().state());
        } else if (result instanceof Message msg) {
            System.out.println("Got Message: " + msg.parts());
        } else {
            System.out.println("Got: " + result);
        }
    }
}
