# SLIM A2A Java

[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/agntcy/slim-a2a-java/badge)](https://scorecard.dev/viewer/?uri=github.com/agntcy/slim-a2a-java)

A2A (Agent-to-Agent) protocol over [SLIM](https://github.com/agntcy/slim) transport for Java.

This library provides an adapter between the [A2A Java SDK](https://github.com/a2aproject/a2a-java) and SLIM's RPC transport, allowing A2A agents to communicate over the SLIM network.

## Project Structure

```
slim-a2a-java/
  buf.yaml / buf.gen.yaml      # Protobuf / SlimRPC code generation config
  Taskfile.yaml                 # Task runner commands
  slim-a2a/                     # Library module
    generated/slimrpc/          # Generated SlimRPC stubs (A2AServiceSlimrpc.java)
    src/main/java/              # Adapter classes
      io/agntcy/slim/a2a/
        SlimA2AHandler.java     # Server-side: RequestHandler -> A2AServiceServer
        SlimA2AClient.java      # Client-side: domain types over SlimRPC
        SlimHelper.java         # SLIM bootstrap helpers
        A2ARpcErrorMapping.java # A2A error <-> RPC error mapping
  examples/echo-agent/          # Echo agent example
    src/main/java/
      io/agntcy/slim/a2a/examples/echo/
        EchoAgentExecutor.java  # Echo AgentExecutor (message / streaming task / cancellable task)
        ServerMain.java         # Server entry point
        ClientMain.java         # Client entry point, one demo per A2A call shape
        A2AEventStream.java     # Decodes a SlimRPC response stream into A2A events
```

## Prerequisites

- Java 21+
- Maven 3.9+
- A running SLIM gateway (e.g. `slimctl up`)
- [Buf CLI](https://buf.build/docs/installation) (for regenerating stubs)
- `protoc-gen-slimrpc-java` on PATH (for regenerating stubs)

## Dependencies

The library depends on:

- `io.agntcy.slim:slim-bindings-java` 2.0.x -- SLIM Java bindings (Foreign Function &amp; Memory API based)
- `org.a2aproject.sdk:a2a-java-sdk-server-common` -- A2A SDK server core
- `org.a2aproject.sdk:a2a-java-sdk-spec-grpc` -- A2A protobuf types and ProtoUtils mappers

These must be available in your local Maven repository. If they are not published to a remote repository, install them locally first:

```bash
# Install SLIM Java bindings
cd /path/to/slim/data-plane/bindings/java
mvn install -DskipTests

# Install A2A Java SDK
cd /path/to/a2a-java
mvn install -DskipTests
```

## Building

```bash
task build
```

## Regenerating SlimRPC Stubs

The generated stubs are checked in under `slim-a2a/generated/slimrpc/`. To regenerate:

```bash
task generate
```

This requires `protoc-gen-slimrpc-java` 2.0.x on your PATH:

```bash
cargo install agntcy-protoc-slimrpc-plugin --version 2.0.0 --locked
```

Older plugin versions emit imports from the pre-2.0 package layout and will not compile against `slim-bindings-java` 2.0.

## Running the Echo Agent Example

### 1. Start a SLIM gateway

```bash
slimctl slim start --endpoint 127.0.0.1:46357
```

### 2. Start the server

In a second terminal:

```bash
task echo-server
```

Wait for `SLIM_A2A_SERVER_READY` to appear.

### 3. Run the client

In a third terminal:

```bash
task echo-client
```

The client runs one demo per A2A call shape, in order:

| Demo | Exercises | What the agent does |
| --- | --- | --- |
| `message` | `SendMessage` | Replies with a single message, no task |
| `stream` | `SendStreamingMessage`, `GetTask` | Runs a task emitting one artifact per word, then completes |
| `cancel` | `SubscribeToTask`, `CancelTask` | Starts a long task, attaches a second subscriber, then cancels it |
| `card` | `GetExtendedAgentCard` | Returns the agent card served over SLIM |

Pass `--demo` to run a subset:

```bash
task echo-client -- -Dexec.args="--demo stream,card"
```

The agent picks its behaviour from a prefix on the message text -- `stream: <text>`
runs a task with artifacts, `slow: <seconds>` runs a cancellable long task, and
anything else gets a plain message reply. See `EchoAgentExecutor`.

## Identity and Node Connections

`SlimHelper.createServer(instance, org, ns, secret, serverAddr)` and the matching
`createChannel` overload are shortcuts for a shared-secret identity over an insecure
connection. With a shared secret, anyone holding the secret can claim any SLIM name,
so production deployments should use per-agent identities.

The general overloads take the identity as an `IdentityProviderConfig` /
`IdentityVerifierConfig` pair (shared secret, JWT, static JWT or SPIRE) and the node
connection as a `ClientConfig`:

```java
Name local = new Name("agntcy", "demo", "server");

// Sign our tokens with an Ed25519 key; kid = sub = did:key works for a did:key mesh.
IdentityProviderConfig provider = new IdentityProviderConfig.Jwt(new ClientJwtAuth(
        new JwtKeyType.Encoding(new JwtKeyConfig(JwtAlgorithm.ED_DSA, JwtKeyFormat.PEM,
                new JwtKeyData.File("/etc/agent/ed25519.pem"))),
        List.of("slim-a2a"), "my-issuer", "did:key:z6Mk...", Duration.ofHours(1)));

// Accept peers whose public keys are in a JWKS file (acts as a trust allowlist).
IdentityVerifierConfig verifier = new IdentityVerifierConfig.Jwt(new JwtAuth(
        new JwtKeyType.Decoding(new JwtKeyConfig(JwtAlgorithm.ED_DSA, JwtKeyFormat.JWKS,
                new JwtKeyData.File("/etc/agent/trusted.jwks.json"))),
        List.of("slim-a2a"), "my-issuer", null, Duration.ofHours(1)));

// TLS to the node (system CA roots) plus a bearer token read from a file.
ClientConfig node = SlimBindings.newSecureClientConfig("https://slim.example.com:443");
node.setAuth(new ClientAuthenticationConfig.StaticJwt(
        new StaticJwtAuth("/etc/agent/node-token.jwt", Duration.ofMinutes(5))));

Server server = SlimHelper.createServer(local, provider, verifier, node);
Channel channel = SlimHelper.createChannel(
        new Name("agntcy", "demo", "client"), local, provider, verifier, node);
```

Every member of a SLIM session needs a compatible identity type, so the server and
its clients must agree on the provider/verifier kind. `SlimHelper.sharedSecretProvider`
and `sharedSecretVerifier` build the shared-secret configs used by the shortcut overloads.

With slim-bindings-java 2.0, the SlimRPC handler context does not expose the caller's
verified identity, so `SlimA2AHandler` still reports an anonymous user to the A2A
`RequestHandler`.

## Architecture

The library follows the same adapter pattern as
[slim-a2a-go](https://github.com/agntcy/slim-a2a-go),
[slim-a2a-python](https://github.com/agntcy/slim-a2a-python), and
[slim-a2a-dotnet](https://github.com/agntcy/slim-a2a-dotnet):

```
            SLIM Transport
                 │
    ┌────────────┼────────────┐
    │   slim-a2a-java Library │
    │            │            │
    │  ┌─────────┴──────────┐ │
    │  │ Generated SlimRPC  │ │   (A2AServiceSlimrpc.java)
    │  │ stubs (buf gen)    │ │
    │  └─────────┬──────────┘ │
    │            │            │
    │  ┌─────────┴──────────┐ │
    │  │ SlimA2AHandler     │ │   Server: adapts RequestHandler -> A2AServiceServer
    │  │ SlimA2AClient      │ │   Client: wraps A2AServiceClient with domain types
    │  │ A2ARpcErrorMapping │ │   Error translation
    │  │ SlimHelper         │ │   SLIM lifecycle bootstrap
    │  └─────────┬──────────┘ │
    └────────────┼────────────┘
                 │
    ┌────────────┼────────────┐
    │    a2a-java SDK         │
    │  RequestHandler         │
    │  AgentExecutor          │
    │  ProtoUtils (mappers)   │
    │  TaskStore / QueueMgr   │
    └─────────────────────────┘
```

Proto types from `a2a-java-sdk-spec-grpc` are reused on the classpath (split-package approach); no custom proto type generation or converter is needed.

## License

Apache-2.0. See [LICENSE](LICENSE).
