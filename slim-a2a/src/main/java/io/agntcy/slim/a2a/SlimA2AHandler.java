// Copyright AGNTCY Contributors (https://github.com/agntcy)
// SPDX-License-Identifier: Apache-2.0

package io.agntcy.slim.a2a;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;

import io.agntcy.slim.bindings.Context;
import io.agntcy.slim.bindings.ResponseSink;
import io.agntcy.slim.bindings.RpcCode;
import io.agntcy.slim.bindings.RpcException;
import org.a2aproject.sdk.grpc.A2AServiceSlimrpc;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.StreamingEventKind;

/**
 * Adapts a {@link RequestHandler} to the generated {@link A2AServiceSlimrpc.A2AServiceServer}
 * contract, bridging A2A SDK domain types and protobuf wire types via {@link ProtoUtils}.
 */
public final class SlimA2AHandler implements A2AServiceSlimrpc.A2AServiceServer {

    private final RequestHandler requestHandler;
    private final org.a2aproject.sdk.spec.AgentCard agentCard;

    /**
     * @param requestHandler the SDK request handler (e.g. DefaultRequestHandler)
     * @param agentCard      the agent card to return for GetExtendedAgentCard
     */
    public SlimA2AHandler(RequestHandler requestHandler, org.a2aproject.sdk.spec.AgentCard agentCard) {
        this.requestHandler = requestHandler;
        this.agentCard = agentCard;
    }

    @Override
    public CompletableFuture<org.a2aproject.sdk.grpc.SendMessageResponse> SendMessage(
            org.a2aproject.sdk.grpc.SendMessageRequest request, Context context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var params = ProtoUtils.FromProto.messageSendParams(request);
                var result = requestHandler.onMessageSend(params, newCallContext());
                return ProtoUtils.ToProto.taskOrMessage(result);
            } catch (A2AError e) {
                throw new CompletionException(A2ARpcErrorMapping.toRpc(e));
            }
        });
    }

    @Override
    public CompletableFuture<Void> SendStreamingMessage(
            org.a2aproject.sdk.grpc.SendMessageRequest request, Context context, ResponseSink sink) {
        return CompletableFuture.runAsync(() -> {
            try {
                var params = ProtoUtils.FromProto.messageSendParams(request);
                Flow.Publisher<StreamingEventKind> publisher =
                        requestHandler.onMessageSendStream(params, newCallContext());
                streamToSink(publisher, sink);
            } catch (A2AError e) {
                throw new CompletionException(A2ARpcErrorMapping.toRpc(e));
            }
        });
    }

    @Override
    public CompletableFuture<org.a2aproject.sdk.grpc.Task> GetTask(
            org.a2aproject.sdk.grpc.GetTaskRequest request, Context context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var params = ProtoUtils.FromProto.taskQueryParams(request);
                var task = requestHandler.onGetTask(params, newCallContext());
                return ProtoUtils.ToProto.task(task);
            } catch (A2AError e) {
                throw new CompletionException(A2ARpcErrorMapping.toRpc(e));
            }
        });
    }

    @Override
    public CompletableFuture<org.a2aproject.sdk.grpc.ListTasksResponse> ListTasks(
            org.a2aproject.sdk.grpc.ListTasksRequest request, Context context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var params = ProtoUtils.FromProto.listTasksParams(request);
                var result = requestHandler.onListTasks(params, newCallContext());
                return ProtoUtils.ToProto.listTasksResult(result);
            } catch (A2AError e) {
                throw new CompletionException(A2ARpcErrorMapping.toRpc(e));
            }
        });
    }

    @Override
    public CompletableFuture<org.a2aproject.sdk.grpc.Task> CancelTask(
            org.a2aproject.sdk.grpc.CancelTaskRequest request, Context context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var params = ProtoUtils.FromProto.cancelTaskParams(request);
                var task = requestHandler.onCancelTask(params, newCallContext());
                return ProtoUtils.ToProto.task(task);
            } catch (A2AError e) {
                throw new CompletionException(A2ARpcErrorMapping.toRpc(e));
            }
        });
    }

    @Override
    public CompletableFuture<Void> SubscribeToTask(
            org.a2aproject.sdk.grpc.SubscribeToTaskRequest request, Context context, ResponseSink sink) {
        return CompletableFuture.runAsync(() -> {
            try {
                var params = ProtoUtils.FromProto.taskIdParams(request);
                Flow.Publisher<StreamingEventKind> publisher =
                        requestHandler.onSubscribeToTask(params, newCallContext());
                streamToSink(publisher, sink);
            } catch (A2AError e) {
                throw new CompletionException(A2ARpcErrorMapping.toRpc(e));
            }
        });
    }

    @Override
    public CompletableFuture<org.a2aproject.sdk.grpc.TaskPushNotificationConfig> CreateTaskPushNotificationConfig(
            org.a2aproject.sdk.grpc.TaskPushNotificationConfig request, Context context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var params = ProtoUtils.FromProto.createTaskPushNotificationConfig(request);
                var result = requestHandler.onCreateTaskPushNotificationConfig(params, newCallContext());
                return ProtoUtils.ToProto.taskPushNotificationConfig(result);
            } catch (A2AError e) {
                throw new CompletionException(A2ARpcErrorMapping.toRpc(e));
            }
        });
    }

    @Override
    public CompletableFuture<org.a2aproject.sdk.grpc.TaskPushNotificationConfig> GetTaskPushNotificationConfig(
            org.a2aproject.sdk.grpc.GetTaskPushNotificationConfigRequest request, Context context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var params = ProtoUtils.FromProto.getTaskPushNotificationConfigParams(request);
                var result = requestHandler.onGetTaskPushNotificationConfig(params, newCallContext());
                return ProtoUtils.ToProto.taskPushNotificationConfig(result);
            } catch (A2AError e) {
                throw new CompletionException(A2ARpcErrorMapping.toRpc(e));
            }
        });
    }

    @Override
    public CompletableFuture<org.a2aproject.sdk.grpc.ListTaskPushNotificationConfigsResponse> ListTaskPushNotificationConfigs(
            org.a2aproject.sdk.grpc.ListTaskPushNotificationConfigsRequest request, Context context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var params = ProtoUtils.FromProto.listTaskPushNotificationConfigsParams(request);
                var result = requestHandler.onListTaskPushNotificationConfigs(params, newCallContext());
                return ProtoUtils.ToProto.listTaskPushNotificationConfigsResponse(result);
            } catch (A2AError e) {
                throw new CompletionException(A2ARpcErrorMapping.toRpc(e));
            }
        });
    }

    @Override
    public CompletableFuture<org.a2aproject.sdk.grpc.AgentCard> GetExtendedAgentCard(
            org.a2aproject.sdk.grpc.GetExtendedAgentCardRequest request, Context context) {
        return CompletableFuture.completedFuture(ProtoUtils.ToProto.agentCard(agentCard));
    }

    @Override
    public CompletableFuture<com.google.protobuf.Empty> DeleteTaskPushNotificationConfig(
            org.a2aproject.sdk.grpc.DeleteTaskPushNotificationConfigRequest request, Context context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var params = ProtoUtils.FromProto.deleteTaskPushNotificationConfigParams(request);
                requestHandler.onDeleteTaskPushNotificationConfig(params, newCallContext());
                return com.google.protobuf.Empty.getDefaultInstance();
            } catch (A2AError e) {
                throw new CompletionException(A2ARpcErrorMapping.toRpc(e));
            }
        });
    }

    private ServerCallContext newCallContext() {
        return new ServerCallContext(ANONYMOUS_USER, Map.of(), Collections.emptySet());
    }

    /**
     * Drains a {@link Flow.Publisher} of streaming events into a SLIM {@link ResponseSink}.
     */
    private void streamToSink(Flow.Publisher<StreamingEventKind> publisher, ResponseSink sink) {
        var subscriber = new java.util.concurrent.CountDownLatch(1);
        var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        publisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEventKind item) {
                try {
                    var proto = ProtoUtils.ToProto.streamResponse(item);
                    sink.sendAsync(proto.toByteArray()).join();
                } catch (Exception e) {
                    subscription.cancel();
                    error.set(e);
                    subscriber.countDown();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                subscriber.countDown();
            }

            @Override
            public void onComplete() {
                subscriber.countDown();
            }
        });
        try {
            subscriber.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(
                    new RpcException.Rpc(RpcCode.CANCELLED, "interrupted", null));
        }
        if (error.get() != null) {
            Throwable t = error.get();
            if (t instanceof A2AError a2aErr) {
                throw new CompletionException(A2ARpcErrorMapping.toRpc(a2aErr));
            }
            if (t instanceof RpcException rpcEx) {
                throw new CompletionException(rpcEx);
            }
            throw new CompletionException(new RpcException.Rpc(RpcCode.INTERNAL,
                    t.getMessage() != null ? t.getMessage() : "stream error", null));
        }
    }

    private static final User ANONYMOUS_USER = new User() {
        @Override
        public boolean isAuthenticated() {
            return false;
        }

        @Override
        public String getUsername() {
            return "anonymous";
        }
    };
}
