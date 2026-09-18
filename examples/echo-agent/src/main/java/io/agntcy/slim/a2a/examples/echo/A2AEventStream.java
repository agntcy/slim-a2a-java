// Copyright AGNTCY Contributors (https://github.com/agntcy)
// SPDX-License-Identifier: Apache-2.0

package io.agntcy.slim.a2a.examples.echo;

import com.google.protobuf.InvalidProtocolBufferException;
import io.agntcy.slim.bindings.slimrpc.ClientResponseStream;
import io.agntcy.slim.bindings.slimrpc.ResponseStreamReader;
import io.agntcy.slim.bindings.slimrpc.RpcException;
import org.a2aproject.sdk.grpc.StreamResponse;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;

/**
 * Adapts the raw SlimRPC response stream returned by
 * {@code SlimA2AClient.sendStreamingMessage} and {@code subscribeToTask} into
 * A2A domain events.
 *
 * <p>Those two methods hand back a {@link ResponseStreamReader} of protobuf
 * bytes rather than domain types, so every caller needs this adapter; it lives
 * in the example to show the decoding, and is a good candidate for promotion
 * into the library.
 */
final class A2AEventStream {

    private final ClientResponseStream<StreamingEventKind> stream;

    private A2AEventStream(ResponseStreamReader reader) {
        this.stream = ClientResponseStream.create(reader, A2AEventStream::decode);
    }

    static A2AEventStream of(ResponseStreamReader reader) {
        return new A2AEventStream(reader);
    }

    /** The next event, or {@code null} once the server has closed the stream. */
    StreamingEventKind next() throws RpcException {
        return stream.recv();
    }

    /** Task id carried by an event, or {@code null} if it carries none. */
    static String taskIdOf(StreamingEventKind event) {
        return switch (event) {
            case Task task -> task.id();
            case TaskStatusUpdateEvent status -> status.taskId();
            case TaskArtifactUpdateEvent artifact -> artifact.taskId();
            default -> null;
        };
    }

    /** One-line rendering of an event, for the example's console output. */
    static String describe(StreamingEventKind event) {
        return switch (event) {
            case Task task ->
                    "Task id=" + task.id() + " state=" + task.status().state();
            case TaskStatusUpdateEvent status ->
                    "TaskStatusUpdate state=" + status.status().state()
                            + " final=" + status.isFinal();
            case TaskArtifactUpdateEvent artifact ->
                    "TaskArtifactUpdate parts=" + artifact.artifact().parts();
            case org.a2aproject.sdk.spec.Message message ->
                    "Message parts=" + message.parts();
            default -> event.toString();
        };
    }

    private static StreamingEventKind decode(byte[] bytes) {
        try {
            return ProtoUtils.FromProto.streamingEventKind(StreamResponse.parseFrom(bytes));
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("malformed A2A stream response", e);
        }
    }
}
