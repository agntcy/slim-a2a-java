// Copyright AGNTCY Contributors (https://github.com/agntcy)
// SPDX-License-Identifier: Apache-2.0

package io.agntcy.slim.a2a;

import io.agntcy.slim.bindings.RpcCode;
import io.agntcy.slim.bindings.RpcException;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.InvalidRequestError;
import org.a2aproject.sdk.spec.MethodNotFoundError;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TaskNotFoundError;
import org.a2aproject.sdk.spec.UnsupportedOperationError;
import org.a2aproject.sdk.spec.PushNotificationNotSupportedError;

/**
 * Bidirectional mapping between A2A SDK error types and SLIM RPC error codes.
 */
public final class A2ARpcErrorMapping {

    private A2ARpcErrorMapping() {}

    public static RpcException.Rpc toRpc(A2AError error) {
        return new RpcException.Rpc(toRpcCode(error), error.getMessage(), null);
    }

    public static A2AError fromRpc(RpcException.Rpc rpc) {
        String message = rpc.message() != null ? rpc.message() : "RPC error";
        return switch (rpc.code()) {
            case NOT_FOUND -> new TaskNotFoundError(message, null);
            case INVALID_ARGUMENT -> new InvalidParamsError(message);
            case UNIMPLEMENTED -> new MethodNotFoundError(null, message, null);
            default -> new org.a2aproject.sdk.spec.InternalError(message);
        };
    }

    private static RpcCode toRpcCode(A2AError error) {
        return switch (error) {
            case TaskNotFoundError ignored -> RpcCode.NOT_FOUND;
            case InvalidParamsError ignored -> RpcCode.INVALID_ARGUMENT;
            case InvalidRequestError ignored -> RpcCode.INVALID_ARGUMENT;
            case MethodNotFoundError ignored -> RpcCode.UNIMPLEMENTED;
            case TaskNotCancelableError ignored -> RpcCode.FAILED_PRECONDITION;
            case PushNotificationNotSupportedError ignored -> RpcCode.FAILED_PRECONDITION;
            case UnsupportedOperationError ignored -> RpcCode.FAILED_PRECONDITION;
            default -> RpcCode.INTERNAL;
        };
    }
}
