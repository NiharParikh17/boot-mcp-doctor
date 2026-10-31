# Compatibility

Which Spring Boot and Spring AI versions each boot-mcp-doctor release works with, what it can do on each, and how that was tested. To move between boot-mcp-doctor versions, see the [migration guides](migrations/README.md).

## Compatibility chart

| Spring Boot | Spring AI | boot-mcp-doctor |
|---|---|---|
| 4.1.0 – 4.1.2 | 2.0.0 – 2.0.2 | **0.1.2** (recommended), 0.1.1 or 0.1.0 |
| 4.0.0 – 4.0.9 | 2.0.0 – 2.0.2 | **0.1.2**. On 4.0.4 and 4.0.5, use Streamable HTTP rather than SSE; see [below](#012). Earlier releases don't support Spring Boot 4.0. |
| 3.x or earlier | any | **Not supported.** boot-mcp-doctor is built on Spring Boot 4's Actuator. |
| 4.x | 1.x | **Not supported.** boot-mcp-doctor needs Spring AI 2.0's MCP annotations. |

**Recommended:** the newest boot-mcp-doctor release, with the newest Spring Boot 4.1 and Spring AI 2.0 releases.

At startup, boot-mcp-doctor 0.1.2 and later logs whether it supports the application's Spring Boot and Spring AI versions, even when it is turned off, and warns when it doesn't. The [guide](guide.md#compatibility) lists the lines.

Every supported pair needs Java 17 or later: the jars are compiled for Java 17. boot-mcp-doctor also only works in Spring MVC (servlet) applications, with Spring AI's synchronous MCP server, `spring.ai.mcp.server.type=SYNC`, which is the default.

## What works on each version

### 0.1.2

Everything in the [guide](guide.md) works on every supported pair: the seven tools, all `boot-mcp-doctor.*` settings, Spring Security authorization, masking and response limits, the compatibility, startup, connection and tool-call logs, and the Streamable HTTP, stateless and SSE transports. `get_health` also works when health is not exposed over HTTP or JMX.

One exception comes from Spring itself: on Spring Boot 4.0.4 and 4.0.5, SSE clients time out while connecting, also in applications without boot-mcp-doctor. These versions use Spring Framework 7.0.6, which breaks the SSE transport of Spring AI's MCP server; Spring Framework 7.0.5 and 7.0.7 work. boot-mcp-doctor warns about it at startup. Use Streamable HTTP, `spring.ai.mcp.server.protocol=STREAMABLE`, or Spring Boot 4.0.6 or later.

### 0.1.1

Spring Boot 4.1 only. The seven tools and all their settings work on every supported pair. Compared with 0.1.2:

- There is no compatibility line at startup and no line for each tool call.
- When a tool fails unexpectedly, the client gets the exception's message.

[Migrating to 0.1.2](migrations/0.1.2.md) has the details.

### 0.1.0

Spring Boot 4.1 only. The seven tools and all their settings work on every supported pair. Compared with 0.1.1:

- `get_health` only works while health is exposed over HTTP or JMX. Otherwise it reports that the health endpoint is not available.
- There are no startup or connection logs.

[Migrating to 0.1.1](migrations/0.1.1.md) has the details.

## How it is tested

On 2026-10-25, before its release, 0.1.2 was tested on every pair of Spring Boot 4.0.0 – 4.1.2 and Spring AI 2.0.0 – 2.0.2, 39 pairs, on Java 17, with both samples' 65 tests, the linkage check included. Every test passed on every pair, except the three SSE scenarios on Spring Boot 4.0.4 and 4.0.5. Those also fail with boot-mcp-doctor turned off, and pass on Spring Boot 4.0.4 when only Spring Framework is changed to 7.0.7.

On 2026-10-24, the 0.1.0 and 0.1.1 releases were tested on the same 39 pairs:

- **With the released jars**, as published on Maven Central, and each release's own sample applications and tests: 59 scenario tests for 0.1.1, which boot the samples in one configuration each and call the tools through a real MCP client, and 6 for 0.1.0.
- **With a linkage check**, `LinkageTests`, which checks that every class, field and method boot-mcp-doctor uses exists on that Spring Boot version, including on code paths no scenario reaches.

Both releases passed every test on every Spring Boot 4.1 pair. On Spring Boot 4.0, the linkage check fails for 0.1.1: when health is not exposed and `management.endpoint.health.status.order` is set, `get_health` calls an API that only Spring Boot 4.1 has.

On every pull request, CI builds and tests on Java 17, 21 and 25 with the newest supported versions, and runs the samples on the oldest supported pair, Spring Boot 4.0.0 with Spring AI 2.0.0. Before each release, the chart is checked again on every supported pair:

```bash
scripts/compatibility.sh 6
```

It finds the supported versions on Maven Central, builds boot-mcp-doctor from your working tree, runs both samples on every pair with six workers, and lists the results in `target/compatibility/results.txt`.
