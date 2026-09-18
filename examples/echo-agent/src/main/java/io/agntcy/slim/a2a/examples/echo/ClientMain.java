// Copyright AGNTCY Contributors (https://github.com/agntcy)
// SPDX-License-Identifier: Apache-2.0

package io.agntcy.slim.a2a.examples.echo;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import io.agntcy.slim.a2a.SlimA2AClient;
import io.agntcy.slim.a2a.SlimHelper;
import io.agntcy.slim.bindings.slimrpc.Channel;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Echo agent client entry point.
 *
 * <p>Runs one demo per A2A call shape. By default every demo runs in order;
 * pass {@code --demo message,stream,cancel,card} to pick a subset.
 *
 * Usage:
 *   task echo-client -- -Dexec.args="--server http://localhost:46357 --demo stream"
 */
public final class ClientMain {

    private static final String DEMO_MESSAGE = "message";
    private static final String DEMO_STREAM = "stream";
    private static final String DEMO_CANCEL = "cancel";
    private static final String DEMO_CARD = "card";
    private static final List<String> ALL_DEMOS =
            List.of(DEMO_MESSAGE, DEMO_STREAM, DEMO_CANCEL, DEMO_CARD);

    public static void main(String[] args) throws Exception {
        String serverAddr = ServerMain.DEFAULT_SERVER_ADDR;
        String remoteInstance = "server";
        Set<String> demos = new LinkedHashSet<>(ALL_DEMOS);
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
            } else if ("--demo".equals(args[i]) && i + 1 < args.length) {
                demos = new LinkedHashSet<>(List.of(args[i + 1].split(",")));
            }
        }

        SlimHelper.initializeDefaults();

        Channel channel = SlimHelper.createChannel(
                "client", ServerMain.ORG, ServerMain.NS,
                remoteInstance, ServerMain.SHARED_SECRET, serverAddr);

        try {
            var client = new SlimA2AClient(channel);

            if (demos.contains(DEMO_MESSAGE)) {
                demoSendMessage(client);
            }
            if (demos.contains(DEMO_STREAM)) {
                String taskId = demoStreaming(client);
                demoGetTask(client, taskId);
            }
            if (demos.contains(DEMO_CANCEL)) {
                demoSubscribeAndCancel(client);
            }
            if (demos.contains(DEMO_CARD)) {
                demoAgentCard(client);
            }

            System.out.println("SLIM_A2A_CLIENT_DONE");
        } finally {
            channel.closeBlocking(Duration.ofSeconds(5));
        }
    }

    /** Unary SendMessage: the agent replies with a Message, no task involved. */
    private static void demoSendMessage(SlimA2AClient client) throws Exception {
        header("SendMessage (unary)");
        EventKind result = client.sendMessage(textParams("Hello from Java A2A client!"));
        if (result instanceof Task task) {
            System.out.println("Got Task: id=" + task.id() + " state=" + task.status().state());
        } else if (result instanceof Message message) {
            System.out.println("Got Message: " + message.parts());
        } else {
            System.out.println("Got: " + result);
        }
    }

    /**
     * SendStreamingMessage: the agent runs a task and pushes status and artifact
     * events until it completes. Returns the task id it reported.
     */
    private static String demoStreaming(SlimA2AClient client) throws Exception {
        header("SendStreamingMessage (unary -> stream)");
        var stream = A2AEventStream.of(
                client.sendStreamingMessage(textParams("stream: one two three")));
        String taskId = null;
        int events = 0;
        StreamingEventKind event;
        while ((event = stream.next()) != null) {
            events++;
            System.out.println("  event " + events + ": " + A2AEventStream.describe(event));
            if (taskId == null) {
                taskId = A2AEventStream.taskIdOf(event);
            }
        }
        System.out.println("Stream ended after " + events + " event(s), taskId=" + taskId);
        if (taskId == null) {
            throw new IllegalStateException("no task id seen on the stream");
        }
        return taskId;
    }

    /** GetTask: fetch the finished task and show what the store kept. */
    private static void demoGetTask(SlimA2AClient client, String taskId) throws Exception {
        header("GetTask");
        Task task = client.getTask(new TaskQueryParams(taskId));
        System.out.println("Task id=" + task.id()
                + " state=" + task.status().state()
                + " artifacts=" + task.artifacts().size()
                + " history=" + task.history().size());
        task.artifacts().forEach(artifact ->
                System.out.println("  artifact: " + artifact.parts()));
    }

    /**
     * SubscribeToTask + CancelTask: start a long-running task, attach a second
     * subscriber to it, then cancel it and watch both streams report the
     * terminal state.
     */
    private static void demoSubscribeAndCancel(SlimA2AClient client) throws Exception {
        header("SubscribeToTask + CancelTask");
        var stream = A2AEventStream.of(client.sendStreamingMessage(textParams("slow: 60")));

        CompletableFuture<String> taskIdFuture = new CompletableFuture<>();
        CompletableFuture<Void> reader = CompletableFuture.runAsync(
                () -> drain("send", stream, taskIdFuture));

        String taskId = taskIdFuture.get(30, TimeUnit.SECONDS);
        System.out.println("Slow task started: " + taskId);

        var subscription = A2AEventStream.of(client.subscribeToTask(new TaskIdParams(taskId)));
        CompletableFuture<Void> subscriber = CompletableFuture.runAsync(
                () -> drain("subscribe", subscription, new CompletableFuture<>()));

        Thread.sleep(500);
        Task cancelled = client.cancelTask(new CancelTaskParams(taskId));
        System.out.println("CancelTask returned state=" + cancelled.status().state());

        reader.get(30, TimeUnit.SECONDS);
        subscriber.get(30, TimeUnit.SECONDS);
    }

    /** GetExtendedAgentCard: the card the server serves over SLIM. */
    private static void demoAgentCard(SlimA2AClient client) throws Exception {
        header("GetExtendedAgentCard");
        AgentCard card = client.getExtendedAgentCard();
        System.out.println("AgentCard name=" + card.name()
                + " version=" + card.version()
                + " skills=" + card.skills().stream().map(skill -> skill.id()).toList());
    }

    /** Prints every event on a stream; completes {@code taskId} with the first id seen. */
    private static void drain(String label, A2AEventStream stream,
                              CompletableFuture<String> taskId) {
        try {
            StreamingEventKind event;
            while ((event = stream.next()) != null) {
                System.out.println("  [" + label + "] " + A2AEventStream.describe(event));
                String id = A2AEventStream.taskIdOf(event);
                if (id != null) {
                    taskId.complete(id);
                }
            }
            System.out.println("  [" + label + "] stream ended");
        } catch (Exception e) {
            taskId.completeExceptionally(e);
            throw new CompletionException(e);
        }
    }

    private static MessageSendParams textParams(String text) {
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .contextId(UUID.randomUUID().toString())
                .parts(List.of(new TextPart(text, null)))
                .build();
        return MessageSendParams.builder().message(message).build();
    }

    private static void header(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
