# SLIM A2A Java

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
        EchoAgentExecutor.java  # Simple echo AgentExecutor
        ServerMain.java         # Server entry point
        ClientMain.java         # Client entry point
```

## Prerequisites

- Java 21+
- Maven 3.9+
- A running SLIM gateway (e.g. `slimctl up`)
- [Buf CLI](https://buf.build/docs/installation) (for regenerating stubs)
- `protoc-gen-slimrpc-java` on PATH (for regenerating stubs)

## Dependencies

The library depends on:

- `io.agntcy.slim:slim-bindings-java` -- SLIM Java bindings (JNA-based)
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
mvn clean install
# or
task build
```

## Regenerating SlimRPC Stubs

The generated stubs are checked in under `slim-a2a/generated/slimrpc/`. To regenerate:

```bash
task generate
```

This requires `protoc-gen-slimrpc-java` on your PATH. Build it from the SLIM repository:

```bash
cd /path/to/slim/data-plane
cargo build --release -p agntcy-protoc-slimrpc-plugin
# Binary: target/release/protoc-gen-slimrpc-java
```

## Running the Echo Agent Example

### 1. Start a SLIM gateway

```bash
slimctl up
```

### 2. Start the server

```bash
task echo-server
# or
mvn -pl examples/echo-agent exec:java \
  -Dexec.mainClass=io.agntcy.slim.a2a.examples.echo.ServerMain
```

### 3. Run the client

```bash
task echo-client
# or
mvn -pl examples/echo-agent exec:java \
  -Dexec.mainClass=io.agntcy.slim.a2a.examples.echo.ClientMain
```

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
