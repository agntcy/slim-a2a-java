// Copyright AGNTCY Contributors (https://github.com/agntcy)
// SPDX-License-Identifier: Apache-2.0

package io.agntcy.slim.a2a;

import java.time.Duration;
import java.util.Map;

import io.agntcy.slim.bindings.Channel;
import io.agntcy.slim.bindings.ResponseStreamReader;
import io.agntcy.slim.bindings.RpcException;
import io.agntcy.slim.bindings.StreamMessage;
import io.agntcy.slim.bindings.slimrpc.ClientResponseStream;
import org.a2aproject.sdk.grpc.A2AServiceSlimrpc;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.CancelTaskParams;

/**
 * A2A client that wraps the generated SlimRPC client stub and exposes
 * SDK domain types rather than protobuf types.
 */
public final class SlimA2AClient {

    private final A2AServiceSlimrpc.A2AServiceClient rpcClient;
    private final Duration defaultTimeout;

    public SlimA2AClient(Channel channel, Duration defaultTimeout) {
        this.rpcClient = new A2AServiceSlimrpc.A2AServiceClientImpl(channel);
        this.defaultTimeout = defaultTimeout;
    }

    public SlimA2AClient(Channel channel) {
        this(channel, Duration.ofSeconds(30));
    }

    /**
     * Sends a message and returns the result (Task or Message).
     */
    public EventKind sendMessage(MessageSendParams params) throws RpcException {
        try {
            var protoRequest = ProtoUtils.ToProto.sendMessageRequest(params);
            var protoResponse = rpcClient.SendMessage(protoRequest, defaultTimeout, null);
            if (protoResponse.hasTask()) {
                return ProtoUtils.FromProto.task(protoResponse.getTask());
            }
            return ProtoUtils.FromProto.message(protoResponse.getMessage());
        } catch (RpcException.Rpc rpc) {
            throw A2ARpcErrorMapping.fromRpc(rpc);
        }
    }

    /**
     * Sends a streaming message and returns a stream reader for events.
     */
    public ResponseStreamReader sendStreamingMessage(MessageSendParams params) throws RpcException {
        var protoRequest = ProtoUtils.ToProto.sendMessageRequest(params);
        return rpcClient.SendStreamingMessage(protoRequest, defaultTimeout, null);
    }

    /**
     * Retrieves a task by query parameters.
     */
    public Task getTask(TaskQueryParams params) throws RpcException {
        try {
            var protoRequest = ProtoUtils.ToProto.getTaskRequest(params);
            var protoResponse = rpcClient.GetTask(protoRequest, defaultTimeout, null);
            return ProtoUtils.FromProto.task(protoResponse);
        } catch (RpcException.Rpc rpc) {
            throw A2ARpcErrorMapping.fromRpc(rpc);
        }
    }

    /**
     * Cancels a task.
     */
    public Task cancelTask(CancelTaskParams params) throws RpcException {
        try {
            var protoRequest = ProtoUtils.ToProto.cancelTaskRequest(params);
            var protoResponse = rpcClient.CancelTask(protoRequest, defaultTimeout, null);
            return ProtoUtils.FromProto.task(protoResponse);
        } catch (RpcException.Rpc rpc) {
            throw A2ARpcErrorMapping.fromRpc(rpc);
        }
    }

    /**
     * Subscribes to task events and returns a stream reader.
     */
    public ResponseStreamReader subscribeToTask(TaskIdParams params) throws RpcException {
        var protoRequest = ProtoUtils.ToProto.subscribeToTaskRequest(params);
        return rpcClient.SubscribeToTask(protoRequest, defaultTimeout, null);
    }

    /**
     * Gets the extended agent card.
     */
    public org.a2aproject.sdk.spec.AgentCard getExtendedAgentCard() throws RpcException {
        try {
            var protoRequest = org.a2aproject.sdk.grpc.GetExtendedAgentCardRequest.getDefaultInstance();
            var protoResponse = rpcClient.GetExtendedAgentCard(protoRequest, defaultTimeout, null);
            return ProtoUtils.FromProto.agentCard(protoResponse);
        } catch (RpcException.Rpc rpc) {
            throw A2ARpcErrorMapping.fromRpc(rpc);
        }
    }
}
