# boot-mcp-doctor

[![CI](https://github.com/NiharParikh17/boot-mcp-doctor/actions/workflows/ci.yml/badge.svg)](https://github.com/NiharParikh17/boot-mcp-doctor/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fcom%2Fnhparikh%2Frepo%2Fboot-mcp-doctor-spring-boot-starter%2Fmaven-metadata.xml&label=Maven%20Central)](https://central.sonatype.com/artifact/com.nhparikh.repo/boot-mcp-doctor-spring-boot-starter)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

A Spring Boot starter that turns your application into an [MCP](https://modelcontextprotocol.io) server for its own operations. Add one dependency and any MCP client can ask the *running* application about its health, metrics, slow endpoints, configuration and log levels, in-process, with no separate server and no copy-pasting Actuator JSON.

> **Status:** early development. 0.1.0 is the first release, and the tools and settings may still change before 1.0.

**Contents:** [Example](#example) · [How it works](#how-it-works) · [Quick start](#quick-start) · [Tools](#tools) · [Configuration](#configuration) · [Logs](#logs) · [Security](#security) · [Building from source](#building-from-source)

Sections marked ▶ expand when you click them.

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

Responses are small and summarized: failures first, units included, lists capped, and secrets masked. Every tool's output is shown under [Tools](#tools).

## How it works

boot-mcp-doctor runs inside your application. Each tool call reads the application's own live state, such as its health checks, Micrometer metrics, `Environment` and logging system, at the moment it is called. Nothing is stored, and nothing is sent anywhere except the response to the caller.

```mermaid
sequenceDiagram
    participant Client as MCP client
    participant Security as Spring Security
    participant Server as MCP endpoint (/mcp)
    participant Doctor as boot-mcp-doctor
    participant App as Your application
    Client->>Security: tools/call get_health
    Security->>Server: authenticated request
    Server->>Doctor: run the tool on the same thread
    Doctor->>Doctor: check the caller's authority
    Doctor->>App: read health, metrics, config or loggers
    App-->>Doctor: live values
    Doctor-->>Client: masked, capped JSON
```

## Requirements

- Java 17 or later
- Spring Boot 4.1
- Spring AI 2.0 (pulled in by the starter)

## Quick start

### 1. Add the starter

<details open>
<summary><b>Maven</b></summary>

```xml
<dependency>
    <groupId>com.nhparikh.repo</groupId>
    <artifactId>boot-mcp-doctor-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

</details>

<details>
<summary><b>Gradle (Kotlin DSL)</b></summary>

```kotlin
dependencies {
    implementation("com.nhparikh.repo:boot-mcp-doctor-spring-boot-starter:0.1.0")
}
```

</details>

<details>
<summary><b>Gradle (Groovy DSL)</b></summary>

```groovy
dependencies {
    implementation 'com.nhparikh.repo:boot-mcp-doctor-spring-boot-starter:0.1.0'
}
```

</details>

### 2. Turn it on

Enable it and choose the Streamable HTTP transport:

```yaml
boot-mcp-doctor:
  enabled: true

spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE
```

Without `protocol: STREAMABLE`, Spring AI 2.0 serves the older SSE transport on `/sse` instead. The `STATELESS` protocol is supported too. Only the default `SYNC` server type is supported for now.

### 3. Decide who may call the tools

- **With Spring Security** (any shared or production environment): protect the MCP endpoint the way you protect the rest of the application. Every tool call must then come from an authenticated caller; see [Security](#security).
- **Without Spring Security** (only on your own machine): boot-mcp-doctor registers no tools until you opt in:

  ```yaml
  boot-mcp-doctor:
    security:
      allow-unauthenticated: true
  ```

### 4. Connect a client

Point any MCP client that supports Streamable HTTP at `http://localhost:8080/mcp`. If the endpoint requires authentication, configure the client to send the same credentials your application expects, such as an `Authorization` header.

The application logs the exact path and port at startup, including any context path, and logs each client that connects; see [Logs](#logs).

<details>
<summary><b>Clients that can only start local processes</b></summary>

Some desktop clients only launch MCP servers as local commands. The [`mcp-remote`](https://www.npmjs.com/package/mcp-remote) package, which is not part of this project, bridges them to an HTTP endpoint. Add an entry like this to the client's MCP server configuration:

```json
{
  "mcpServers": {
    "order-service": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:8080/mcp"]
    }
  }
}
```

</details>

<details>
<summary><b>Notes</b></summary>

- The tools read the application in-process. You do **not** need to expose the Actuator `metrics`, `env` or `loggers` endpoints over HTTP.
- boot-mcp-doctor registers its tools without Spring AI's annotation scanner. If your application has no `@McpTool` beans of its own, you can set `spring.ai.mcp.server.annotation-scanner.enabled=false` to skip the scanner and the startup warnings it logs when it finds nothing.

</details>

## Tools

| Tool | Inputs | Returns |
|---|---|---|
| `get_health` | none | Overall status and per-component status. Components that are not UP come first, with masked details. |
| `get_slow_endpoints` | `limit` (default 5) | Slowest HTTP endpoints by mean latency, with request count, mean and max in ms, and 5xx error rate. |
| `list_metrics` | optional `prefix` | Metric names, alphabetically. |
| `get_metric` | `name`, optional `tags` (`KEY:VALUE`) | Current values with units (ms, MB), and the tags that can narrow it. Unknown names return the closest matches. |
| `get_environment` | optional `prefix` | Effective property values, where each one is set (file and line, environment variable and so on) and the active profiles. Always masked. |
| `get_loggers` | optional `prefix` | Configured and effective log levels, plus logger groups. |
| `set_log_level` | `logger`, `level` | Previous and new level. **Write tool:** only registered when `read-only` is `false`, Spring Security is present and `security.write-authority` is set. |

Without a prefix, `get_environment` returns only properties set in your own config files, and `get_loggers` returns only ROOT and loggers with a configured level.

The outputs below are real responses from the [sample app](#building-from-source), run with Spring Security, an `ops` user and `write-authority` set. `get_health` and `get_slow_endpoints` are shown in the [Example](#example).

<details>
<summary><b>list_metrics</b> with <code>prefix: "jvm.memory"</code></summary>

```json
{
  "summary": "4 metrics starting with 'jvm.memory'.",
  "names": ["jvm.memory.committed", "jvm.memory.max", "jvm.memory.usage.after.gc", "jvm.memory.used"],
  "omittedNames": 0
}
```

</details>

<details>
<summary><b>get_metric</b> with <code>name: "jvm.memory.used"</code>, <code>tags: ["area:heap"]</code></summary>

```json
{
  "name": "jvm.memory.used",
  "found": true,
  "summary": "jvm.memory.used: VALUE 44.3 MB.",
  "description": "The amount of used memory",
  "measurements": [
    { "statistic": "VALUE", "value": 44.3, "unit": "MB" }
  ],
  "availableTags": { "id": ["G1 Eden Space", "G1 Old Gen", "G1 Survivor Space"] },
  "suggestions": []
}
```

</details>

<details>
<summary><b>get_metric</b> with a misspelled name, <code>"jvm.memory.usd"</code></summary>

```json
{
  "name": "jvm.memory.usd",
  "found": false,
  "summary": "No metric named 'jvm.memory.usd'.",
  "description": null,
  "measurements": [],
  "availableTags": {},
  "suggestions": ["jvm.memory.used", "jvm.memory.max", "jvm.buffer.memory.used", "jvm.gc.pause", "jvm.memory.committed"]
}
```

</details>

<details>
<summary><b>get_environment</b> with <code>prefix: "spring.security"</code> (note the masked password)</summary>

```json
{
  "summary": "3 properties match prefix 'spring.security'.",
  "activeProfiles": [],
  "properties": [
    { "name": "spring.security.user.name", "value": "ops", "origin": "class path resource [application.yml] - 6:13" },
    { "name": "spring.security.user.password", "value": "******", "origin": "class path resource [application.yml] - 7:17" },
    { "name": "spring.security.user.roles", "value": "OPS_ADMIN", "origin": "class path resource [application.yml] - 8:14" }
  ],
  "omittedProperties": 0
}
```

</details>

<details>
<summary><b>get_loggers</b> with <code>prefix: "com.nhparikh"</code></summary>

```json
{
  "summary": "7 loggers start with 'com.nhparikh'.",
  "levels": ["OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE"],
  "loggers": [
    { "name": "com.nhparikh", "configuredLevel": null, "effectiveLevel": "INFO" },
    { "name": "com.nhparikh.repo", "configuredLevel": null, "effectiveLevel": "INFO" },
    { "name": "com.nhparikh.repo.bootmcpdoctor", "configuredLevel": null, "effectiveLevel": "INFO" },
    { "name": "com.nhparikh.repo.bootmcpdoctor.autoconfigure", "configuredLevel": null, "effectiveLevel": "INFO" },
    { "name": "com.nhparikh.repo.bootmcpdoctor.autoconfigure.ToolSelector", "configuredLevel": null, "effectiveLevel": "INFO" },
    { "name": "com.nhparikh.repo.bootmcpdoctor.demo", "configuredLevel": null, "effectiveLevel": "INFO" },
    { "name": "com.nhparikh.repo.bootmcpdoctor.demo.DemoApplication", "configuredLevel": null, "effectiveLevel": "INFO" }
  ],
  "groups": [],
  "omittedLoggers": 0
}
```

</details>

<details>
<summary><b>set_log_level</b> with <code>logger: "com.nhparikh.repo.bootmcpdoctor.demo"</code>, <code>level: "DEBUG"</code></summary>

```json
{
  "summary": "Set com.nhparikh.repo.bootmcpdoctor.demo from inherited to DEBUG (effective DEBUG).",
  "logger": "com.nhparikh.repo.bootmcpdoctor.demo",
  "changed": true,
  "previousLevel": null,
  "newLevel": "DEBUG",
  "effectiveLevel": "DEBUG"
}
```

</details>

## Configuration

| Property | Default | Description |
|---|---|---|
| `boot-mcp-doctor.enabled` | `false` | Nothing is registered unless this is `true`. |
| `boot-mcp-doctor.read-only` | `true` | When `true`, tools that change state (`set_log_level`) are not registered at all. |
| `boot-mcp-doctor.tools.include` | empty | Names of the tools to register. Empty means all. |
| `boot-mcp-doctor.tools.exclude` | empty | Names of tools never to register. Wins over `include`. |
| `boot-mcp-doctor.security.allow-unauthenticated` | `false` | Lets read tools be called without authentication, including without Spring Security. For local development only. |
| `boot-mcp-doctor.security.read-authority` | empty | Authority a caller needs for read tools, such as `ROLE_OPS`. Empty means any authenticated caller. |
| `boot-mcp-doctor.security.write-authority` | empty | Authority a caller needs for tools that change state, such as `ROLE_OPS_ADMIN`. Those tools are not registered until this is set. |
| `boot-mcp-doctor.masking.additional-keys` | empty | Extra key patterns to mask, matched case-insensitively anywhere in the key. |
| `boot-mcp-doctor.limits.max-items` | `50` | Maximum number of entries in any list or map a tool returns. |

boot-mcp-doctor also follows Actuator's access settings:
- `management.endpoint.<id>.access=none` (or `management.endpoints.access.default` / `max-permitted`) removes that endpoint's tools. The IDs are `health`, `metrics`, `env` and `loggers`.
- `read-only` access removes the write tools.

<details>
<summary><b>Every setting in one <code>application.yml</code></b></summary>

```yaml
boot-mcp-doctor:
  enabled: true
  read-only: true                 # false also registers set_log_level, given the security settings below
  tools:
    include: []                   # for example [get_health, get_slow_endpoints]; empty means all
    exclude: []                   # for example [get_environment]
  security:
    allow-unauthenticated: false  # true only for local development
    read-authority: ROLE_OPS      # leave unset to allow any authenticated caller
    write-authority: ROLE_OPS_ADMIN
  masking:
    additional-keys: [iban, ssn]  # masked on top of password, secret, key, token, credential and auth
  limits:
    max-items: 50
```

</details>

## Logs

Once the application is ready, boot-mcp-doctor logs where its tools are served and who may call them. It also logs each MCP client that connects:

```text
INFO  c.n.r.b.autoconfigure.StartupLog  : boot-mcp-doctor is running at /api/xyz/mcp on port 8090 (Streamable HTTP) with 6 read-only tools: get_environment, get_health, get_loggers, get_metric, get_slow_endpoints, list_metrics. Tool calls need a caller authenticated by Spring Security.
INFO  c.n.r.b.a.ConnectionLogFilter     : MCP client connected: Visual Studio Code 1.105.1 (protocol 2025-06-18, session bf022b6e, user ops, from 10.0.0.7)
```

- **Startup.** The path includes the context path and the DispatcherServlet path, so it is what your MCP client needs after `http://<host>:<port>`. Instead of that line, a warning is logged when no MCP server serves the tools or no tools are registered. A second warning follows it when `security.allow-unauthenticated=true` lets callers in without authenticating.
- **Connections.** A client is logged when its `initialize` request succeeds, with the name and version it reports, the protocol version, the start of its session ID, the user Spring Security authenticated and its address. Requests that Spring Security turns away are not logged. Client names and versions are sent by the client, so line breaks and other control characters are replaced and long values are shortened. Connections are logged in servlet (Spring MVC) applications.
- **Turning them off.** Set `logging.level.com.nhparikh.repo.bootmcpdoctor.autoconfigure.ConnectionLogFilter=WARN` to stop the connection lines, or `logging.level.com.nhparikh.repo.bootmcpdoctor=WARN` to keep only warnings.

## Security

These tools describe the internals of a running application to whoever can reach the MCP endpoint. Treat that endpoint like an Actuator endpoint.

- **Off by default.** Nothing is registered until you set `boot-mcp-doctor.enabled=true`.
- **Authenticated callers only.** Spring Security decides how callers authenticate, for example with an API key or OAuth2 bearer tokens. boot-mcp-doctor then checks every tool call itself: the caller must be authenticated, even if the MCP endpoint was accidentally left open, and must hold `security.read-authority` or `security.write-authority` when those are set. A refused call returns a "Not authorized" error instead of data. Tool calls run on the request thread, which is where Spring Security keeps the caller.
- **No tools without authentication.** Without Spring Security on the classpath, boot-mcp-doctor registers no tools unless `security.allow-unauthenticated=true`, and even then only read tools.
- **Read-only by default.** `set_log_level` is the only tool that changes state. It is only registered when `boot-mcp-doctor.read-only=false`, Spring Security is present, `security.write-authority` is set, and Actuator's `loggers` access is not `read-only` or `none`.
- **Secrets are always masked.** Keys containing `password`, `secret`, `key`, `token`, `credential` or `auth` are replaced with `******`, as are passwords embedded in URIs such as `jdbc:postgresql://user:pass@host/db`. Any `SanitizingFunction` beans you define for Actuator are applied as well. Masking cannot be turned off; you can only add patterns.
- **No full dumps.** `get_environment` needs a prefix to look beyond your own config files. Every response is capped at `limits.max-items` entries, and long strings are truncated.
- **Protect the endpoint too.** Configure Spring Security to require authentication for the MCP endpoint (`/mcp`, or `/sse` and `/mcp/message` for SSE), so unauthenticated requests are turned away before they reach the MCP server at all.

<details>
<summary><b>Example: protect the endpoint with Spring Security</b></summary>

A minimal setup with HTTP Basic. For production, prefer OAuth2 bearer tokens or API keys in the same filter chain. MCP clients don't send CSRF tokens, so CSRF protection is turned off for `/mcp` only (add `/mcp/message` if you use SSE).

```java
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf((csrf) -> csrf.ignoringRequestMatchers("/mcp"))
            .authorizeHttpRequests((requests) -> requests.anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .build();
    }
}
```

With this in place, a request to `/mcp` without credentials gets HTTP 401, and an authenticated caller can use the read tools. Add `boot-mcp-doctor.security.write-authority` (and a user holding that authority) to allow `set_log_level`.

</details>

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

Contributions are welcome. [CONTRIBUTING.md](CONTRIBUTING.md) covers the development setup, how to add a tool, and what a pull request needs. Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

[Apache License 2.0](LICENSE)
