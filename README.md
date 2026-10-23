# boot-mcp-doctor

[![CI](https://github.com/NiharParikh17/boot-mcp-doctor/actions/workflows/ci.yml/badge.svg)](https://github.com/NiharParikh17/boot-mcp-doctor/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

A Spring Boot starter that turns your application into an [MCP](https://modelcontextprotocol.io) server for its own operations. Add one dependency and any MCP client can ask the *running* application about its health, metrics, slow endpoints, configuration and log levels, in-process, with no separate server and no copy-pasting Actuator JSON.

> **Status:** early development. Not yet published to Maven Central.

## Example

Ask your assistant *"Why is order-service slow?"*. It calls `get_slow_endpoints` and gets a short, structured answer:

```json
{
  "summary": "Slowest of 2 endpoints: POST /checkout averages 2104.1 ms over 2 requests (max 2133.0 ms, 0.0% errors).",
  "endpoints": [
    { "method": "POST", "uri": "/checkout", "count": 2, "meanMs": 2104.1, "maxMs": 2133.0, "errorRatePercent": 0.0 },
    { "method": "GET", "uri": "/orders", "count": 4, "meanMs": 8.8, "maxMs": 32.4, "errorRatePercent": 0.0 }
  ],
  "totalEndpoints": 2
}
```

Then it can call `get_health` to see that a downstream dependency is failing:

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

Responses are small and summarized: failures first, units included, lists capped, and secrets masked.

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

Without `protocol: STREAMABLE`, Spring AI 2.0 serves the older SSE transport on `/sse` instead. The `STATELESS` protocol is supported too. Only the default `SYNC` server type is supported for now.

The tools read the application in-process. You do **not** need to expose the Actuator `metrics`, `env` or `loggers` endpoints over HTTP.

## Tools

| Tool | Inputs | Returns |
|---|---|---|
| `get_health` | none | Overall status and per-component status. Components that are not UP come first, with masked details. |
| `get_slow_endpoints` | `limit` (default 5) | Slowest HTTP endpoints by mean latency, with request count, mean and max in ms, and 5xx error rate. |
| `list_metrics` | optional `prefix` | Metric names, alphabetically. |
| `get_metric` | `name`, optional `tags` (`KEY:VALUE`) | Current values with units (ms, MB), and the tags that can narrow it. Unknown names return the closest matches. |
| `get_environment` | optional `prefix` | Effective property values, where each one is set (file and line, environment variable and so on) and the active profiles. Always masked. |
| `get_loggers` | optional `prefix` | Configured and effective log levels, plus logger groups. |
| `set_log_level` | `logger`, `level` | Previous and new level. **Write tool:** only registered when `read-only` is `false`. |

Without a prefix, `get_environment` returns only properties set in your own config files, and `get_loggers` returns only ROOT and loggers with a configured level.

## Configuration

| Property | Default | Description |
|---|---|---|
| `boot-mcp-doctor.enabled` | `false` | Nothing is registered unless this is `true`. |
| `boot-mcp-doctor.read-only` | `true` | When `true`, tools that change state (`set_log_level`) are not registered at all. |
| `boot-mcp-doctor.tools.include` | empty | Names of the tools to register. Empty means all. |
| `boot-mcp-doctor.tools.exclude` | empty | Names of tools never to register. Wins over `include`. |
| `boot-mcp-doctor.masking.additional-keys` | empty | Extra key patterns to mask, matched case-insensitively anywhere in the key. |
| `boot-mcp-doctor.limits.max-items` | `50` | Maximum number of entries in any list or map a tool returns. |

boot-mcp-doctor also follows Actuator's access settings:
- `management.endpoint.<id>.access=none` (or `management.endpoints.access.default` / `max-permitted`) removes that endpoint's tools. The IDs are `health`, `metrics`, `env` and `loggers`.
- `read-only` access removes the write tools.

## Security

These tools describe the internals of a running application to whoever can reach the MCP endpoint. Treat that endpoint like an Actuator endpoint.

- **Off by default.** Nothing is registered until you set `boot-mcp-doctor.enabled=true`.
- **Read-only by default.** `set_log_level` is the only tool that changes state. It is not registered unless you set `boot-mcp-doctor.read-only=false`, and even then not if Actuator's `loggers` access is `read-only` or `none`.
- **Secrets are always masked.** Keys containing `password`, `secret`, `key`, `token`, `credential` or `auth` are replaced with `******`, as are passwords embedded in URIs such as `jdbc:postgresql://user:pass@host/db`. Any `SanitizingFunction` beans you define for Actuator are applied as well. Masking cannot be turned off; you can only add patterns.
- **No full dumps.** `get_environment` needs a prefix to look beyond your own config files. Every response is capped at `limits.max-items` entries, and long strings are truncated.
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

`./mvnw verify` also checks that line coverage of the auto-configuration module stays at 85% or more; the HTML report is written to `boot-mcp-doctor-autoconfigure/target/site/jacoco/`. Releases are covered in [RELEASING.md](RELEASING.md).

## Feedback

Found a bug, have an idea for a tool, or stuck on setup? [Open an issue](https://github.com/NiharParikh17/boot-mcp-doctor/issues/new/choose). Please report security problems privately, as described in [SECURITY.md](SECURITY.md).

## License

[Apache License 2.0](LICENSE)
