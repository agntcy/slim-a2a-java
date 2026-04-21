// Copyright AGNTCY Contributors (https://github.com/agntcy)
// SPDX-License-Identifier: Apache-2.0

package io.agntcy.slim.a2a.examples.echo;

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
        for (int i = 0; i < args.length; i++) {
            if ("--server".equals(args[i]) && i + 1 < args.length) {
                serverAddr = args[i + 1];
            }
        }

        SlimHelper.initializeDefaults();

        Channel channel = SlimHelper.createChannel(
                "client", ServerMain.ORG, ServerMain.NS,
                "server", ServerMain.SHARED_SECRET, serverAddr);

        var client = new SlimA2AClient(channel);

        String contextId = UUID.randomUUID().toString();
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .contextId(contextId)
                .parts(List.of(new TextPart("Hello from Java A2A client!", null)))
                .build();

        MessageSendParams params = MessageSendParams.builder()
                .message(message)
                .build();

        System.out.println("Sending message to echo agent...");
        EventKind result = client.sendMessage(params);

        if (result instanceof Task task) {
            System.out.println("Got Task: id=" + task.id()
                    + " state=" + task.status().state());
        } else if (result instanceof Message msg) {
            System.out.println("Got Message: " + msg.parts());
        } else {
            System.out.println("Got: " + result);
        }

        System.out.println("Fetching agent card...");
        var agentCard = client.getExtendedAgentCard();
        System.out.println("Agent: " + agentCard.name()
                + " - " + agentCard.description());

        System.out.println("SLIM_A2A_CLIENT_DONE");
    }
}
