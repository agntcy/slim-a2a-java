// Copyright AGNTCY Contributors (https://github.com/agntcy)
// SPDX-License-Identifier: Apache-2.0

package io.agntcy.slim.a2a.examples.echo;

import java.util.List;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Minimal echo agent: echoes the user's text input back as a message.
 */
public final class EchoAgentExecutor implements AgentExecutor {

    @Override
    public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
        String userInput = context.getUserInput();
        String reply;
        if (userInput.startsWith("/shout ")) {
            reply = "Echo (SHOUT): " + userInput.substring(7).toUpperCase();
        } else if (userInput.startsWith("/reverse ")) {
            reply = "Echo (Reversed): " + new StringBuilder(userInput.substring(9)).reverse().toString();
        } else {
            reply = "Echo: " + userInput;
        }
        System.out.println("[EchoAgent] request contextId=" + context.getContextId()
                + " userText=" + userInput);

        emitter.sendMessage(List.of(new TextPart(reply, null)));

        System.out.println("[EchoAgent] sent reply (" + reply.length() + " chars)");
    }

    @Override
    public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
        emitter.cancel();
    }
}
