// Copyright AGNTCY Contributors (https://github.com/agntcy)
// SPDX-License-Identifier: Apache-2.0

package io.agntcy.slim.a2a.examples.echo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Echo agent with three behaviours, selected by a prefix on the user's text:
 *
 * <ul>
 *   <li>{@code stream: <text>} -- runs as a task: submitted, working, one artifact
 *       per word, completed. Exercises the streaming RPCs.</li>
 *   <li>{@code slow: <seconds>} -- runs as a long task that polls for cancellation,
 *       so a client can cancel it mid-flight.</li>
 *   <li>anything else -- replies with a single message (no task).</li>
 * </ul>
 */
public final class EchoAgentExecutor implements AgentExecutor {

    static final String STREAM_PREFIX = "stream:";
    static final String SLOW_PREFIX = "slow:";

    /** Cancellation flags for in-flight {@code slow:} tasks, keyed by task id. */
    private final Map<String, AtomicBoolean> cancellations = new ConcurrentHashMap<>();

    @Override
    public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
        String userInput = context.getUserInput();
        System.out.println("[EchoAgent] request contextId=" + context.getContextId()
                + " taskId=" + emitter.getTaskId() + " userText=" + userInput);

        if (userInput.startsWith(STREAM_PREFIX)) {
            streamingEcho(userInput.substring(STREAM_PREFIX.length()).trim(), emitter);
        } else if (userInput.startsWith(SLOW_PREFIX)) {
            slowEcho(userInput.substring(SLOW_PREFIX.length()).trim(), emitter);
        } else {
            String reply = "Echo: " + userInput;
            emitter.sendMessage(List.of(new TextPart(reply, null)));
            System.out.println("[EchoAgent] sent reply (" + reply.length() + " chars)");
        }
    }

    @Override
    public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
        AtomicBoolean flag = cancellations.get(emitter.getTaskId());
        if (flag != null) {
            flag.set(true);
        }
        System.out.println("[EchoAgent] cancel requested taskId=" + emitter.getTaskId());
        emitter.cancel();
    }

    /** Echoes one artifact per word so the client sees a multi-event stream. */
    private void streamingEcho(String text, AgentEmitter emitter) {
        emitter.submit();
        emitter.startWork();
        String[] words = text.isEmpty() ? new String[] {""} : text.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            emitter.addArtifact(List.of(new TextPart("Echo[" + (i + 1) + "]: " + words[i], null)));
            System.out.println("[EchoAgent] emitted artifact " + (i + 1) + "/" + words.length);
        }
        emitter.complete();
        System.out.println("[EchoAgent] task completed with " + words.length + " artifact(s)");
    }

    /**
     * Works for up to {@code seconds}, polling for cancellation. If the client
     * cancels, {@link #cancel} has already emitted the terminal state, so this
     * returns without emitting another one.
     */
    private void slowEcho(String seconds, AgentEmitter emitter) {
        int limit = parseSeconds(seconds);
        AtomicBoolean cancelled = cancellations.computeIfAbsent(
                emitter.getTaskId(), id -> new AtomicBoolean());
        emitter.submit();
        emitter.startWork();
        System.out.println("[EchoAgent] slow task working for up to " + limit + "s");
        try {
            for (int tick = 0; tick < limit * 10 && !cancelled.get(); tick++) {
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cancellations.remove(emitter.getTaskId());
        }
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            System.out.println("[EchoAgent] slow task stopped early (cancelled)");
            return;
        }
        emitter.addArtifact(List.of(new TextPart("Echo: finished after " + limit + "s", null)));
        emitter.complete();
        System.out.println("[EchoAgent] slow task completed");
    }

    private static int parseSeconds(String value) {
        try {
            return Math.max(1, Math.min(300, Integer.parseInt(value.trim())));
        } catch (NumberFormatException e) {
            return 30;
        }
    }
}
