# boot-mcp-doctor

A Spring Boot starter that turns your application into an [MCP](https://modelcontextprotocol.io) server for its own operations. Add one dependency and any MCP client can ask the *running* application about its health, in-process, with no separate server and no copy-pasting Actuator JSON.

> **Status:** early development. Not yet published to Maven Central. `get_health` works today; metrics, configuration and logger tools are next.

## Example

Ask your assistant *"Is order-service healthy?"*. It calls `get_health` and gets a short, structured answer:

```json
{
  "status": "DOWN",
  "summary": "DOWN: 1 of 6 components are not UP: inventory (DOWN).",
  "components": [
    {
      "name": "inventory",
      "status": "DOWN",
      "details": { "url": "http://inventory.internal:8081", "error": "Connection refused" }
    },
    { "name": "diskSpace", "status": "UP", "details": {} },
    { "name": "livenessState", "status": "UP", "details": {} },
    { "name": "ping", "status": "UP", "details": {} },
    { "name": "readinessState", "status": "UP", "details": {} },
    { "name": "ssl", "status": "UP", "details": {} }
  ],
  "omittedComponents": 0
}
```

Failing components come first with their details. Healthy ones are listed by name only, and secrets are masked.

## Requirements

- Java 17 or later
- Spring Boot 4.1
- Spring AI 2.0 (pulled in by the starter)

## Quick start

Add the starter:

```xml
<dependency>
    <groupId>io.github.niharparikh17</groupId>
    <artifactId>boot-mcp-doctor-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Turn it on and choose the Streamable HTTP transport:

```yaml
boot-mcp-doctor:
  enabled: true

spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE
```

Then point any MCP client that supports Streamable HTTP at `http://localhost:8080/mcp`.

Without `protocol: STREAMABLE`, Spring AI 2.0 serves the older SSE transport on `/sse` instead.

## Tools

| Tool | Inputs | Returns |
|---|---|---|
| `get_health` | none | Overall status and per-component status. Components that are not UP come first, with masked details. |

Planned: `get_slow_endpoints`, `get_metric`, `list_metrics`, `get_environment`, `get_loggers` and an opt-in `set_log_level`.

## Configuration

| Property | Default | Description |
|---|---|---|
| `boot-mcp-doctor.enabled` | `false` | Nothing is registered unless this is `true`. |
| `boot-mcp-doctor.masking.additional-keys` | empty | Extra key patterns to mask, matched case-insensitively anywhere in the key. |
| `boot-mcp-doctor.limits.max-items` | `50` | Maximum number of entries in any list or map a tool returns. |

## Security

These tools describe the internals of a running application to whoever can reach the MCP endpoint. Treat that endpoint like an Actuator endpoint.

- **Off by default.** Nothing is registered until you set `boot-mcp-doctor.enabled=true`.
- **Read-only.** Every current tool only reads state. Tools that change state will be opt-in and are not registered otherwise.
- **Secrets are always masked.** Keys containing `password`, `secret`, `key`, `token`, `credential` or `auth` are replaced with `******`, as are passwords embedded in URIs such as `jdbc:postgresql://user:pass@host/db`. Masking cannot be turned off; you can only add patterns.
- **Responses are capped** at `limits.max-items` entries, and long strings are truncated.
- **Protect the endpoint.** The MCP endpoint has no authentication of its own. Enable this only in trusted environments, such as local development or behind your existing network and security controls.

## Building from source

```bash
./mvnw verify
```

Run the sample app, which has a deliberately slow `/checkout` endpoint and a failing `inventory` health check:

```bash
./mvnw install -DskipTests
./mvnw -pl samples/demo-app spring-boot:run
```

## License

[Apache License 2.0](LICENSE)
