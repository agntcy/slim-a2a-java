// Copyright AGNTCY Contributors (https://github.com/agntcy)
// SPDX-License-Identifier: Apache-2.0

package io.agntcy.slim.a2a.examples.echo;

import java.util.List;
import java.util.concurrent.Executors;

import io.agntcy.slim.a2a.SlimA2AHandler;
import io.agntcy.slim.a2a.SlimHelper;
import io.agntcy.slim.bindings.Server;
import org.a2aproject.sdk.grpc.A2AServiceSlimrpc;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.StreamingEventKind;

/**
 * Echo agent server entry point.
 *
 * Usage:
 *   task echo-server -- -Dexec.args="--server localhost:9090"
 */
public final class ServerMain {

    static final String ORG = "agntcy";
    static final String NS = "slim-a2a";
    static final String SHARED_SECRET = "demo-shared-secret";
    static final String DEFAULT_SERVER_ADDR = "localhost:9090";

    public static void main(String[] args) throws Exception {
        String serverAddr = DEFAULT_SERVER_ADDR;
        String instance = "server";
        for (int i = 0; i < args.length; i++) {
            if ("--server".equals(args[i]) && i + 1 < args.length) {
                serverAddr = args[i + 1];
            } else if ("--instance".equals(args[i]) && i + 1 < args.length) {
                instance = args[i + 1];
            }
        }

        SlimHelper.initializeDefaults();

        AgentCard agentCard = AgentCard.builder()
                .name("Echo Agent")
                .description("A simple agent that echoes the user's input")
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder()
                        .extendedAgentCard(true)
                        .build())
                .skills(List.of(
                        AgentSkill.builder()
                                .id("echo")
                                .name("Echo")
                                .description("Echoes the input text back")
                                .build()
                ))
                .build();

        var agentExecutor = new EchoAgentExecutor();
        var taskStore = new InMemoryTaskStore();
        var eventBus = new MainEventBus();
        var queueManager = new InMemoryQueueManager(taskStore, eventBus);
        var pushConfigStore = new InMemoryPushNotificationConfigStore();

        PushNotificationSender noOpPush = event -> {};

        var eventProcessor = new MainEventBusProcessor(eventBus, taskStore, noOpPush, queueManager);
        var processorThread = new Thread(eventProcessor, "event-bus-processor");
        processorThread.setDaemon(true);
        processorThread.start();

        var executor = Executors.newCachedThreadPool();
        var requestHandler = DefaultRequestHandler.create(
                agentExecutor, taskStore, queueManager, pushConfigStore,
                eventProcessor, executor, executor);

        var handler = new SlimA2AHandler(requestHandler, agentCard);

        Server rpcServer = SlimHelper.createServer(instance, ORG, NS, SHARED_SECRET, serverAddr);
        A2AServiceSlimrpc.registerA2AServiceServer(rpcServer, handler);

        System.out.println("Echo agent server starting on SLIM gateway " + serverAddr);
        System.out.println("SLIM_A2A_SERVER_READY");
        rpcServer.serve();
    }
}
