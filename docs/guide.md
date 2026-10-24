# boot-mcp-doctor guide

This guide explains what boot-mcp-doctor does, when to use it, how to set it up for your application, and what every setting does. For the five-minute version, start with the [quick start](../README.md#quick-start). This guide covers version 0.1.1. For the Spring Boot and Spring AI versions it works with, see [Compatibility](compatibility.md); to upgrade from an earlier version, see the [migration guides](migrations/README.md).

**Contents**

1. [What it is](#what-it-is)
2. [When to use it](#when-to-use-it), and [when it will not work](#when-it-will-not-work)
3. [How to set it up](#how-to-set-it-up)
4. [Settings reference](#settings-reference)
5. [Recipes](#recipes)
6. [Connecting clients](#connecting-clients)
7. [Logs](#logs)
8. [What each tool needs](#what-each-tool-needs)
9. [Troubleshooting](#troubleshooting)
10. [Scenarios covered by tests](#scenarios-covered-by-tests)
11. [FAQ](#faq)

## What it is

boot-mcp-doctor is a Spring Boot starter. Added to an application, it gives that application an MCP (Model Context Protocol) endpoint with tools that describe the application itself: its health, metrics, slowest endpoints, configuration and log levels. An assistant such as GitHub Copilot or Claude connects to the endpoint and calls the tools while it helps you investigate a problem.

Four pieces work together:

| Piece | Job |
|---|---|
| Spring AI's MCP server (pulled in by the starter) | Serves the MCP endpoint, such as `/mcp`, and speaks the protocol. |
| boot-mcp-doctor | Provides the tools, decides which ones are registered, checks every call, masks secrets and caps response sizes. |
| Spring Security (optional, yours) | Decides who the caller is. |
| Your application | Supplies the data: health indicators, Micrometer metrics, `Environment`, logging system. |

What happens when a client calls a tool:

1. The client sends an HTTP POST to the MCP endpoint.
2. If the application uses Spring Security, it authenticates the request, or rejects it, usually with HTTP 401.
3. The MCP server runs the tool on the same request thread.
4. boot-mcp-doctor checks the caller against `boot-mcp-doctor.security.*`. A refused call returns `Not authorized: ...` instead of data.
5. The tool reads live values in-process, the same way Actuator does, without calling any HTTP endpoint.
6. Secrets are masked, lists and long strings are cut to size, and the response goes back to the client.

What it does **not** do:

- It is not a separate process or server. It only exists inside the application you add it to.
- It never describes other services, only the application it runs in.
- It stores nothing and sends nothing anywhere except the response to the caller.
- It does not need any Actuator endpoint to be exposed over HTTP.
- It does not change the application, unless you turn on `set_log_level`.

## When to use it

| Where the application runs | Recommended setup |
|---|---|
| Your own machine | No Spring Security needed: `security.allow-unauthenticated=true`, read-only. See [Local development](#local-development). |
| A shared development, test or staging environment | Spring Security protecting the endpoint, read-only. See [HTTP Basic](#http-basic-for-a-shared-environment) or [OAuth2 bearer tokens](#oauth2-bearer-tokens). |
| Production | Spring Security with tokens or API keys, `security.read-authority` so only operators can call the tools, and `set_log_level` only for a separate admin authority, if at all. Consider [hiding configuration](#never-expose-configuration). |

The tools describe whatever is in the application, including configuration values (masked), health details and logger names. Treat the MCP endpoint like an Actuator endpoint: only enable it where the people who can reach it may see that information.

### When it will not work

| Situation | What happens | What to do |
|---|---|---|
| Spring Boot 3 or earlier, or Spring AI 1.x | boot-mcp-doctor is built against Spring Boot 4.1 and Spring AI 2.0, whose packages and APIs differ from earlier versions. It does not run on older ones. | Upgrade the application, or don't use boot-mcp-doctor yet. |
| Java 16 or earlier | The jars are built for Java 17. | Run on Java 17 or later. |
| A WebFlux application | The starter brings Spring MVC and Tomcat. When both are present, Spring Boot runs Spring MVC, so your WebFlux application would change underneath you. | Only add it to Spring MVC (servlet) applications. |
| An application without a web server, such as a batch job | The starter starts Tomcat so that the MCP endpoint can be reached over HTTP. | Use it in web applications only. |
| `spring.ai.mcp.server.type=ASYNC` | boot-mcp-doctor only provides synchronous tools, so nothing is served. The startup log warns: `no synchronous MCP server is running`. | Leave `type` at its default, `SYNC`. |
| `spring.ai.mcp.server.protocol` not set | Spring AI 2.0 falls back to the older SSE transport, so `/mcp` does not exist and clients get HTTP 404 from it. The startup line says `(SSE)`. | Set `protocol: STREAMABLE`. |
| No Spring Security and `allow-unauthenticated` not set | No tools are registered, by design. The log says `registered no tools because nothing authenticates callers`. | Add Spring Security, or opt in for local development. |
| Several instances behind a load balancer | With `STREAMABLE` or `SSE`, each session lives in the memory of one instance. If the next request lands on another instance, it fails with HTTP 404 `Session not found`. | Use sticky sessions, or `protocol: STATELESS`. Either way, each answer describes only the instance that served it. |
| A client that can only sign in through its own OAuth flow | Your application does not publish OAuth discovery metadata, so the client falls back to `/authorize` on your application and the sign-in fails. | Send the token in a header instead; see [Connecting clients](#connecting-clients). |
| Login through a form or session cookie | MCP clients cannot fill in a login form. | Accept HTTP Basic, bearer tokens or API keys on the MCP endpoint. |

## How to set it up

**1. Add the dependency.** Use the Maven or Gradle snippet from the [quick start](../README.md#1-add-the-starter).

**2. Turn it on and choose the transport.**

```yaml
boot-mcp-doctor:
  enabled: true

spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE
```

**3. Decide who may call the tools.** Pick the row that matches your application:

| Spring Security on the classpath? | Settings | Read tools | `set_log_level` |
|---|---|---|---|
| No | defaults | Not registered | Not registered |
| No | `security.allow-unauthenticated: true` | Anyone who can reach the endpoint | Never registered |
| Yes | defaults | Any authenticated caller | Not registered |
| Yes | `security.read-authority: ROLE_OPS` | Authenticated callers with `ROLE_OPS` | Not registered |
| Yes | `security.allow-unauthenticated: true` | Anyone your filter chain lets through, including unauthenticated callers | Not registered |
| Yes | `read-only: false` and `security.write-authority: ROLE_OPS_ADMIN` | As in the rows above | Authenticated callers with `ROLE_OPS_ADMIN` |

`read-authority` wins over `allow-unauthenticated`: once a read authority is set, callers need it. `set_log_level` only checks the write authority, not the read authority.

**4. Start the application and read the startup line.** It tells you the exact path, port and transport, and who may call the tools:

```text
boot-mcp-doctor is running at /mcp on port 8080 (Streamable HTTP) with 6 read-only tools: get_environment, get_health, get_loggers, get_metric, get_slow_endpoints, list_metrics. Tool calls need a caller authenticated by Spring Security.
```

**5. Connect a client** to `http://<host>:<port><path>`, for example `http://localhost:8080/mcp`. See [Connecting clients](#connecting-clients).

**6. Then:** the log shows `MCP client connected: ...` for each client, and you can ask the assistant questions such as:

- "Is order-service healthy? If not, why?" (`get_health`)
- "Which endpoints are slowest, and how many requests fail?" (`get_slow_endpoints`)
- "How much heap is in use?" (`list_metrics`, `get_metric`)
- "Which database URL is the application using, and where is it set?" (`get_environment`)
- "Turn on DEBUG for com.example.orders." (`get_loggers`, `set_log_level`, if allowed)

## Settings reference

Every boot-mcp-doctor setting lives under `boot-mcp-doctor`. A few settings from Spring AI, Spring Boot and Actuator also decide how it behaves; they are listed after.

### `boot-mcp-doctor.enabled`

- **Default:** `false`
- **What:** The main switch. Nothing is registered, not even a bean, unless it is `true`.
- **When:** Set it to `true` in every environment where you want the tools. A common pattern is `false` in `application.yml` and `true` in a profile, such as `application-staging.yml`.
- **How:** `boot-mcp-doctor.enabled: true`
- **Then:** The tools are registered and the startup line appears.
- **Won't work if:** Actuator or the MCP server library is missing from the classpath. The starter brings both, so this only happens when you depend on `boot-mcp-doctor-autoconfigure` directly.

### `boot-mcp-doctor.read-only`

- **Default:** `true`
- **What:** While `true`, tools that change the application are not registered at all, so a client cannot even see them. Today that is `set_log_level`.
- **When:** Set it to `false` only when someone should be able to change log levels through the assistant.
- **How:** `boot-mcp-doctor.read-only: false`, together with `security.write-authority`.
- **Then:** `set_log_level` appears in the startup line, followed by `set_log_level needs the <authority> authority`.
- **Won't work if:** Spring Security is missing, `security.write-authority` is not set, or Actuator's `loggers` access is `read-only` or `none`. The first two log a warning that starts with `Tools that change state, such as set_log_level, were not registered`.

### `boot-mcp-doctor.tools.include`

- **Default:** empty, meaning all tools
- **What:** A list of tool names to register. Anything not listed is left out.
- **When:** You want a small, fixed set of tools, for example only health and latency in production.
- **How:** `boot-mcp-doctor.tools.include: [get_health, get_slow_endpoints]`
- **Then:** Only those tools are registered, and the startup line lists them.
- **Won't work if:** A name is misspelled, or the tool is removed by another setting, for example `set_log_level` in read-only mode. Including a tool never overrides security, read-only or Actuator access. Such names are logged once: `boot-mcp-doctor.tools names tools that will not be registered: [...]`, followed by the tools that can be registered.

Tool names: `get_health`, `get_slow_endpoints`, `get_metric`, `list_metrics`, `get_environment`, `get_loggers`, `set_log_level`.

### `boot-mcp-doctor.tools.exclude`

- **Default:** empty
- **What:** Tool names never to register. Exclude wins over include.
- **When:** You want everything except one or two tools, for example no `get_environment`.
- **How:** `boot-mcp-doctor.tools.exclude: [get_environment]`
- **Then:** The startup line no longer lists them.
- **Won't work if:** A name is misspelled; it is logged the same way as for `include`.

### `boot-mcp-doctor.security.allow-unauthenticated`

- **Default:** `false`
- **What:** Lets read tools be called without authentication.
  - Without Spring Security, it is the only way to register any tools. `set_log_level` is still never registered.
  - With Spring Security, unauthenticated callers may call the read tools, unless `read-authority` is set.
- **When:** Local development, or a machine nobody else can reach. Never on a shared network.
- **How:** `boot-mcp-doctor.security.allow-unauthenticated: true`
- **Then:** The startup line says `Tool calls are not authenticated` or `Read tools also accept unauthenticated callers`, and a warning follows: `boot-mcp-doctor.security.allow-unauthenticated=true: anyone who can reach the MCP endpoint can call the read tools without authenticating.`
- **Won't work if:** Your Spring Security configuration rejects unauthenticated requests to the MCP endpoint first. That is a good thing: Spring Security always gets the first say.

### `boot-mcp-doctor.security.read-authority`

- **Default:** empty, meaning any authenticated caller
- **What:** The authority a caller needs for the read tools, such as `ROLE_OPS` or `SCOPE_ops.read`.
- **When:** Not every authenticated user of the application should see its internals.
- **How:** `boot-mcp-doctor.security.read-authority: ROLE_OPS`
- **Then:** The startup line says `Read tools need the ROLE_OPS authority`. A caller without it gets `Not authorized: get_health requires the ROLE_OPS authority.`
- **Won't work if:** The name does not match exactly. The comparison is case-sensitive and includes the prefix:
  - Spring Security roles become `ROLE_<role>`, so `roles: OPS` means `ROLE_OPS`.
  - OAuth2 scopes become `SCOPE_<scope>` by default, so the scope `ops.read` means `SCOPE_ops.read`.
  - Without Spring Security there are no authorities, so this setting has no effect.

### `boot-mcp-doctor.security.write-authority`

- **Default:** empty
- **What:** The authority a caller needs for tools that change the application. Until it is set, those tools are not registered, even with `read-only: false`.
- **When:** You want `set_log_level`, limited to a few people. Use a different authority than `read-authority`.
- **How:** `boot-mcp-doctor.security.write-authority: ROLE_OPS_ADMIN`
- **Then:** With `read-only: false`, the startup line ends with `set_log_level needs the ROLE_OPS_ADMIN authority`.
- **Won't work if:** Spring Security is missing, `read-only` is still `true`, or the authority name does not match, as for `read-authority`.

### `boot-mcp-doctor.masking.additional-keys`

- **Default:** empty
- **What:** Extra words to mask, on top of the built-in ones: `password`, `secret`, `key`, `token`, `credential` and `auth`. A key is masked when it contains any of them anywhere, ignoring case. The value becomes `******`. Passwords inside URIs, such as `jdbc:postgresql://user:pass@host/db`, are masked too, and any `SanitizingFunction` beans you defined for Actuator are applied as well.
- **When:** Your configuration or health details carry sensitive values under other names, such as `iban`, `ssn` or `private-url`.
- **How:** `boot-mcp-doctor.masking.additional-keys: [iban, ssn]`
- **Then:** `get_environment` and `get_health` show `******` for those keys.
- **Good to know:**
  - Entries are plain text, not regular expressions.
  - Matching is generous on purpose: `key` also masks `cache.key-prefix`.
  - Masking cannot be turned off; you can only add words.

### `boot-mcp-doctor.limits.max-items`

- **Default:** `50`
- **What:** The most entries any list or map in a response may have: health components, metric names, properties, loggers, tag values and details. Most responses say how many entries were left out. Strings longer than 500 characters are cut, independently of this setting.
- **When:** Lower it to keep responses small for an assistant; raise it if you have many components or properties and want to see them all.
- **How:** `boot-mcp-doctor.limits.max-items: 20`
- **Then:** Lists are cut at that size. `get_slow_endpoints` never returns more endpoints than this, whatever `limit` the client asks for.
- **Won't work if:** It is below 1: the application fails to start with `boot-mcp-doctor.limits.max-items must be at least 1`.

### Settings from Spring AI, Spring Boot and Actuator

| Setting | Default | What it means for boot-mcp-doctor |
|---|---|---|
| `spring.ai.mcp.server.protocol` | SSE in practice | `STREAMABLE` serves `/mcp` and is what most clients expect. `STATELESS` serves `/mcp` without sessions, which suits several instances. `SSE` is the older transport on `/sse` and `/mcp/message`. Spring AI 2.0 uses SSE when this is not set, even though its documentation lists Streamable HTTP as the default, so always set it. |
| `spring.ai.mcp.server.type` | `SYNC` | Must stay `SYNC`. |
| `spring.ai.mcp.server.enabled` | `true` | `false` turns the MCP server off, so the tools are not served; the startup log warns. |
| `spring.ai.mcp.server.streamable-http.mcp-endpoint` | `/mcp` | Moves the Streamable HTTP and stateless endpoint. |
| `spring.ai.mcp.server.sse-endpoint`, `sse-message-endpoint` | `/sse`, `/mcp/message` | Move the SSE endpoints. |
| `spring.ai.mcp.server.name`, `version` | `mcp-server`, `1.0.0` | What clients show as the server's name and version. Set the name to your application's name. |
| `spring.ai.mcp.server.annotation-scanner.enabled` | `true` | Scans for your own `@McpTool` beans. boot-mcp-doctor does not need it; if you have no tools of your own, set it to `false` to skip the scan and its startup warnings. |
| `server.servlet.context-path`, `spring.mvc.servlet.path` | none | Both come before the MCP endpoint in the URL: a context path of `/api/xyz` makes it `/api/xyz/mcp`. The startup line includes them. |
| `management.endpoint.<id>.access` | `unrestricted` | `none` removes that endpoint's tools; `read-only` removes its write tools. The IDs are `health`, `metrics`, `env` and `loggers`. |
| `management.endpoints.access.default`, `max-permitted` | `unrestricted` | Apply the same to every endpoint, as in Actuator. |
| `logging.level.com.nhparikh.repo.bootmcpdoctor` | `INFO` | `WARN` keeps only warnings. See [Logs](#logs). |

Actuator's exposure settings (`management.endpoints.web.exposure.*`) don't matter: the tools read the application in-process, whether or not the endpoints are exposed. `get_health` uses Actuator's health endpoint when Spring Boot has created it, which it only does while health is exposed, and otherwise reads the health contributors directly, so hiding health from HTTP callers doesn't affect it. A separate `management.server.port` and `management.endpoint.health.show-details` make no difference either.

## Recipes

Each recipe shows the settings for one situation. Combine them as needed.

### Local development

No Spring Security; read tools for anyone who can reach your machine.

```yaml
boot-mcp-doctor:
  enabled: true
  security:
    allow-unauthenticated: true

spring:
  ai:
    mcp:
      server:
        name: order-service
        protocol: STREAMABLE
```

### HTTP Basic for a shared environment

Add `spring-boot-starter-security` and protect every request, including `/mcp`. MCP clients don't send CSRF tokens, so CSRF is turned off for the MCP endpoint only. Without that, every POST to `/mcp` is rejected with HTTP 401, even with the right password. With SSE, exempt `/mcp/message` instead.

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

```yaml
boot-mcp-doctor:
  enabled: true
  security:
    read-authority: ROLE_OPS
```

Clients send `Authorization: Basic <base64 of user:password>`. The [secured sample](../samples/secured-demo-app) uses this setup, with an `ops` user for the read tools and an `admin` user who may also change log levels.

### OAuth2 bearer tokens

For applications that are OAuth2 resource servers and accept JWT access tokens. Add `spring-boot-starter-security-oauth2-resource-server`.

```java
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests((requests) -> requests.anyRequest().authenticated())
            .oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://login.example.com/realms/example

boot-mcp-doctor:
  enabled: true
  security:
    read-authority: SCOPE_ops.read
```

- Clients send `Authorization: Bearer <access token>`. The token must be an access token from that issuer, not an ID token.
- Requests with a bearer token skip CSRF checks automatically, so no CSRF setting is needed.
- If your application already has a filter chain that requires a token for every request, the MCP endpoint is covered; you only need the properties.
- By default the token's scopes become `SCOPE_<scope>` authorities. If you map roles with your own converter, use those names.
- The connection log shows the token's subject (`sub` claim) as the user; for client-credentials tokens that is usually the client ID.
- Access tokens expire, often within an hour. Clients that take a fixed header need a new token when it does.
- The [secured sample](../samples/secured-demo-app) accepts bearer tokens next to HTTP Basic once a JWT decoder is configured. `JwtBearerTokenTests` shows tokens with and without the right scope, an expired token and a token from another issuer.

### Let admins change log levels

```yaml
boot-mcp-doctor:
  enabled: true
  read-only: false
  security:
    read-authority: ROLE_OPS
    write-authority: ROLE_OPS_ADMIN
```

Requires Spring Security. Changes last until the application restarts and only affect the instance that handled the call. Use `RESET` as the level to remove a logger's level, so it inherits from its parent again.

### Only health and latency

```yaml
boot-mcp-doctor:
  enabled: true
  tools:
    include: [get_health, get_slow_endpoints]
```

### Never expose configuration

Either leave the tool out:

```yaml
boot-mcp-doctor:
  tools:
    exclude: [get_environment]
```

or turn off Actuator's `env` endpoint, which also removes the tool:

```yaml
management:
  endpoint:
    env:
      access: none
```

### An application with a context path

```yaml
server:
  servlet:
    context-path: /api/xyz
```

The endpoint becomes `/api/xyz/mcp`, and the startup line says so. Spring Security matches paths without the context path, so the CSRF rule above stays `/mcp`.

### Several instances

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STATELESS
```

Every request stands alone, so any instance can answer it. The answer describes the instance that served it, so two calls can describe two different instances.

### Extra masking

```yaml
boot-mcp-doctor:
  masking:
    additional-keys: [iban, ssn, private-url]
```

## Connecting clients

### Find the URL

The URL is `http://<host>:<port>` followed by the path in the startup line. For example, `boot-mcp-doctor is running at /api/xyz/mcp on port 8090` means `http://<host>:8090/api/xyz/mcp`. Use `https` if the application, or a proxy in front of it, serves HTTPS.

### Check it with curl first

This sends the same first request a client sends. Set `TOKEN` first, or swap the `Authorization` header for what your application expects.

```bash
curl -si -X POST http://localhost:8080/mcp -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}'
```

| Response | Meaning |
|---|---|
| HTTP 200 with an `Mcp-Session-Id` header | Everything works. Clients will connect. |
| HTTP 401 | The credentials were missing or rejected. For bearer tokens, the `WWW-Authenticate` header's `error_description` says why, for example an expired token or the wrong issuer. With HTTP Basic or API keys, a CSRF rejection also comes back as 401 and looks exactly like a wrong password: if the same credentials work for a GET elsewhere in the application, exempt `/mcp` from CSRF. |
| HTTP 403 | Authenticated, but an authorization rule in your filter chain refused the request, for example `hasRole` on `/mcp`. |
| HTTP 404 from `/mcp` | Credentials accepted, but there is no Streamable HTTP endpoint at that path. Usually `spring.ai.mcp.server.protocol` is not set; check the startup line. Applications that include error messages in responses show `No static resource mcp`. |
| HTTP 404 on a later request, `Session not found` | The session is unknown to the instance that answered: the application restarted, or another instance answered. See [Several instances](#several-instances). |
| HTTP 400, `Invalid Accept headers` | The request did not accept both `application/json` and `text/event-stream`. |

### VS Code (GitHub Copilot)

In `.vscode/mcp.json`, or your user `mcp.json`. VS Code asks for the token once and stores it.

```json
{
  "inputs": [
    { "id": "order-token", "type": "promptString", "description": "Token for order-service", "password": true }
  ],
  "servers": {
    "order-service": {
      "type": "http",
      "url": "http://localhost:8080/mcp",
      "headers": { "Authorization": "Bearer ${input:order-token}" }
    }
  }
}
```

### IntelliJ IDEA and other JetBrains IDEs (GitHub Copilot)

Open the file from the GitHub Copilot icon in the status bar: **Edit Settings** → **Model Context Protocol** → **Configure**. Headers go under `requestInit`:

```json
{
  "servers": {
    "order-service": {
      "url": "http://localhost:8080/mcp",
      "requestInit": {
        "headers": { "Authorization": "Bearer <access token>" }
      }
    }
  }
}
```

The token is stored in this file in plain text; it is the IDE's own settings, not part of your project. Replace it when it expires.

### Clients that only start local processes

Some desktop clients only start MCP servers as local commands. The [`mcp-remote`](https://www.npmjs.com/package/mcp-remote) package, which is not part of this project, bridges them to an HTTP endpoint:

```json
{
  "mcpServers": {
    "order-service": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:8080/mcp", "--header", "Authorization:${AUTH_HEADER}"],
      "env": { "AUTH_HEADER": "Bearer <access token>" }
    }
  }
}
```

Keeping the value in `env` avoids problems with spaces in arguments. For a plain `http://` URL on another host, `mcp-remote` also needs `--allow-http`; only use that on a private network.

### "Wants to authenticate" prompts

If a client asks to sign in, or opens `/authorize` on your application, the client got HTTP 401: the token was missing, in the wrong place in the configuration, or rejected. Cancel the prompt and run the [curl check](#check-it-with-curl-first) with the same token. A client's built-in sign-in only works when the application publishes OAuth discovery metadata for its identity provider, which boot-mcp-doctor does not do.

## Logs

Since 0.1.1, boot-mcp-doctor logs one line when the application is ready and one line for each client that connects.

### At startup

```text
boot-mcp-doctor is running at /api/xyz/mcp on port 8090 (Streamable HTTP) with 7 tools: get_environment, get_health, get_loggers, get_metric, get_slow_endpoints, list_metrics, set_log_level. Read tools need the ROLE_OPS authority; set_log_level needs the ROLE_OPS_ADMIN authority.
```

| Part | Meaning |
|---|---|
| `at /api/xyz/mcp` | The path to put after `http://<host>:<port>`, including the context path and DispatcherServlet path. |
| `on port 8090` | The application's HTTP port. |
| `(Streamable HTTP)` | The transport: `Streamable HTTP`, `stateless Streamable HTTP` or `SSE`. With SSE, the path shown is the one clients connect to, `/sse`. |
| `with 7 tools: ...` | Every registered tool. When none of them changes the application, it says so, as in `with 6 read-only tools`. |
| The last sentence | Who may call them: `Tool calls are not authenticated`, `Tool calls need a caller authenticated by Spring Security`, `Read tools need the <authority> authority` or `Read tools also accept unauthenticated callers`, plus the authority `set_log_level` needs. |

Warnings at startup:

| Warning | Meaning |
|---|---|
| `boot-mcp-doctor is enabled, but its tools are not served: no synchronous MCP server is running.` | `spring.ai.mcp.server.enabled` is `false`, or `type` is not `SYNC`. |
| `boot-mcp-doctor is running at ... with no tools.` | Security, read-only, include/exclude or Actuator access left nothing. An earlier warning usually says which. |
| `boot-mcp-doctor registered no tools because nothing authenticates callers` | No Spring Security and no `allow-unauthenticated`. |
| `boot-mcp-doctor.security.allow-unauthenticated=true: anyone who can reach the MCP endpoint can call the read tools without authenticating.` | A reminder that the tools are open. |
| `Tools that change state, such as set_log_level, were not registered: ...` | `read-only: false`, but Spring Security or `write-authority` is missing. |
| `boot-mcp-doctor.tools names tools that will not be registered: [...]` | A name in `include` or `exclude` is misspelled or removed by another setting. |

### When a client connects

```text
MCP client connected: Visual Studio Code 1.105.1 (protocol 2025-06-18, session bf022b6e, user ops, from 10.0.0.7)
```

| Part | Meaning |
|---|---|
| `Visual Studio Code 1.105.1` | The name and version the client reports about itself. |
| `protocol 2025-06-18` | The MCP protocol version the client asked for. |
| `session bf022b6e` | The first 8 characters of the session ID; left out for `STATELESS`. |
| `user ops` | The user Spring Security authenticated, or `not authenticated`. |
| `from 10.0.0.7` | The client's address, as the application sees it. Behind a proxy, set `server.forward-headers-strategy` to see the real one. |

- **When it is logged:** a line is written when a client's `initialize` request succeeds. Requests that Spring Security turns away are not logged.
- **Client-supplied values:** the client name and version come from the client, so line breaks and other control characters are replaced with `?` and values are cut at 100 characters.
- **Applications:** connections are logged in Spring MVC applications.

### Turning them off

| Setting | Effect |
|---|---|
| `logging.level.com.nhparikh.repo.bootmcpdoctor.autoconfigure.ConnectionLogFilter: WARN` | No connection lines. |
| `logging.level.com.nhparikh.repo.bootmcpdoctor: WARN` | Only warnings from boot-mcp-doctor. |

## What each tool needs

| Tool | Reads | Needs | When the source is missing |
|---|---|---|---|
| `get_health` | Actuator's health endpoint, or the health contributors directly when health is not exposed | Actuator's health module, which the starter brings | `Health not available...` |
| `list_metrics`, `get_metric` | Micrometer's `MeterRegistry` | A `MeterRegistry` bean, which Actuator creates | `Metrics not available...` |
| `get_slow_endpoints` | The `http.server.requests` metric | A `MeterRegistry` and at least one handled request | `No HTTP requests recorded yet...` |
| `get_environment` | The Spring `Environment` | Nothing extra | Always available |
| `get_loggers`, `set_log_level` | Spring Boot's `LoggingSystem` | Spring Boot's logging, which is on by default | `Logging system not available.` |

Things worth knowing about individual tools:

- **`get_health`** includes the details of components that are not UP, masked, even when `management.endpoint.health.show-details` is `never` or health is not exposed. Healthy components are listed by name and status only.
- **`get_slow_endpoints`** ranks by mean latency since startup and leaves out the MCP and Actuator endpoints. Its `max` only covers roughly the last two minutes, so it can be 0 for endpoints that were not called recently. `limit` defaults to 5.
- **`get_metric`** reports times in ms and sizes in MB. An unknown name returns the closest matching names instead.
- **`get_environment`** without a prefix only returns properties from the application's own config files. With a prefix, such as `spring.datasource`, it returns any property, including environment variables, with where each value comes from.
- **`set_log_level`** accepts `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL`, `OFF` or `RESET`, for a logger or a logger group.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| No startup line at all | `boot-mcp-doctor.enabled` is not `true`, the version is older than 0.1.1, or boot-mcp-doctor's logging is set above `INFO`. | Set it in the active profile; check the version and logging levels. |
| Startup warns `registered no tools because nothing authenticates callers` | No Spring Security. | Add Spring Security, or set `security.allow-unauthenticated: true` for local development. |
| HTTP 404 from `/mcp`, sometimes with `No static resource mcp` | The transport is SSE because `protocol` is not set; the startup line says `(SSE)`. | `spring.ai.mcp.server.protocol: STREAMABLE`. |
| Client asks to sign in, or opens `/authorize` on the application | The client got HTTP 401. | Send the token in a header; run the curl check. |
| HTTP 401 with `error_description="Jwt expired..."` or a wrong issuer | The token is rejected. | Get a fresh access token from the issuer in `issuer-uri`. |
| HTTP 401 with HTTP Basic or an API key, although the credentials work for GET requests | CSRF protection rejected the POST. | Exempt `/mcp` from CSRF, as in the [HTTP Basic recipe](#http-basic-for-a-shared-environment). |
| HTTP 403 | An authorization rule in your filter chain. | Check `authorizeHttpRequests` for the MCP endpoint. |
| A tool returns `Not authorized: ... requires an authenticated caller.` | The MCP endpoint lets unauthenticated requests through, and boot-mcp-doctor refused the call. | Require authentication for `/mcp` in Spring Security. |
| A tool returns `Not authorized: ... requires the X authority.` | The caller lacks `read-authority` or `write-authority`. | Grant it, or check the exact name, including the `ROLE_` or `SCOPE_` prefix. |
| `set_log_level` is missing | `read-only` is `true`, `write-authority` is not set, Spring Security is missing, or `loggers` access is restricted. | See [`read-only`](#boot-mcp-doctorread-only). |
| A tool is missing | `include`, `exclude` or Actuator access removed it. | Check the startup warnings and the tool list. |
| `get_slow_endpoints` says no requests were recorded | The application has not served any HTTP request since it started. | Call an endpoint and ask again. |
| HTTP 404 `Session not found` | The application restarted, or another instance answered. | Reconnect; for several instances, see [Several instances](#several-instances). |

## Scenarios covered by tests

Every behavior in this guide is checked by a test. Each test boots one of the two sample applications with one configuration, connects a real MCP client, and checks the startup line, the tool results, the HTTP responses and the logs. They run with `./mvnw verify`, so on every pull request. Each test class doubles as an example: its `@SpringBootTest(properties = ...)` is the configuration, and its assertions are what you will see.

Without Spring Security, in [`samples/demo-app`](../samples/demo-app):

| Scenario | Test |
|---|---|
| The demo as it ships: every read tool, the logs, `allow-unauthenticated` | [`DemoApplicationTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/DemoApplicationTests.java) |
| No `allow-unauthenticated`, so no tools | [`NoAuthenticationOptInTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/NoAuthenticationOptInTests.java) |
| `read-only: false` without Spring Security, so no `set_log_level` | [`WriteToolsWithoutSpringSecurityTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/WriteToolsWithoutSpringSecurityTests.java) |
| `tools.include`, `tools.exclude` and a misspelled name | [`ToolSelectionTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/ToolSelectionTests.java) |
| `management.endpoint.<id>.access=none` | [`ActuatorAccessTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/ActuatorAccessTests.java) |
| `management.endpoints.access.max-permitted=none` | [`MaxPermittedAccessTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/MaxPermittedAccessTests.java) |
| `show-details=never` and a separate management port | [`ActuatorExposureTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/ActuatorExposureTests.java) |
| Health not exposed, and `get_health` still works | [`HealthNotExposedTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/HealthNotExposedTests.java) |
| Masking: built-in keys, `additional-keys`, URI passwords and health details | [`MaskingTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/MaskingTests.java) |
| `limits.max-items` | [`ResponseLimitTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/ResponseLimitTests.java) |
| `limits.max-items=0` stops the application from starting | [`InvalidMaxItemsTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/InvalidMaxItemsTests.java) |
| `protocol: SSE` | [`SseProtocolTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/SseProtocolTests.java) |
| `protocol` not set | [`ProtocolNotSetTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/ProtocolNotSetTests.java) |
| `protocol: STATELESS` | [`StatelessProtocolTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/StatelessProtocolTests.java) |
| A context path | [`ContextPathTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/ContextPathTests.java) |
| A DispatcherServlet path | [`ServletPathTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/ServletPathTests.java) |
| A custom MCP endpoint | [`CustomEndpointTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/CustomEndpointTests.java) |
| `boot-mcp-doctor.enabled=false` | [`DisabledTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/DisabledTests.java) |
| `spring.ai.mcp.server.type=ASYNC` | [`AsyncServerTypeTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/AsyncServerTypeTests.java) |
| `spring.ai.mcp.server.enabled=false` | [`McpServerDisabledTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/McpServerDisabledTests.java) |
| The connection log turned off | [`ConnectionLogOffTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/ConnectionLogOffTests.java) |
| The HTTP responses from the curl check | [`HttpResponsesTests`](../samples/demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/demo/HttpResponsesTests.java) |

With Spring Security, in [`samples/secured-demo-app`](../samples/secured-demo-app):

| Scenario | Test |
|---|---|
| The secured demo as it ships: HTTP Basic, `read-authority` and `write-authority`, HTTP 401 for missing or wrong credentials, refused calls, `set_log_level` and `RESET` | [`SecuredDemoApplicationTests`](../samples/secured-demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/secured/SecuredDemoApplicationTests.java) |
| boot-mcp-doctor's defaults: any authenticated caller | [`AnyAuthenticatedCallerTests`](../samples/secured-demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/secured/AnyAuthenticatedCallerTests.java) |
| `read-only: false` without `write-authority` | [`WriteWithoutAuthorityTests`](../samples/secured-demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/secured/WriteWithoutAuthorityTests.java) |
| The MCP endpoint accidentally left open in Spring Security | [`OpenEndpointTests`](../samples/secured-demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/secured/OpenEndpointTests.java) |
| `allow-unauthenticated` with Spring Security | [`AllowUnauthenticatedTests`](../samples/secured-demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/secured/AllowUnauthenticatedTests.java) |
| `read-authority` wins over `allow-unauthenticated` | [`ReadAuthorityWinsTests`](../samples/secured-demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/secured/ReadAuthorityWinsTests.java) |
| OAuth2 JWT bearer tokens: scopes, a write scope only, an expired token, another issuer | [`JwtBearerTokenTests`](../samples/secured-demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/secured/JwtBearerTokenTests.java) |
| No CSRF exemption: HTTP Basic gets 401, bearer tokens still work | [`CsrfTests`](../samples/secured-demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/secured/CsrfTests.java) |
| A role required by the filter chain: HTTP 403 | [`AuthorizationRuleTests`](../samples/secured-demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/secured/AuthorizationRuleTests.java) |
| A context path | [`SecuredContextPathTests`](../samples/secured-demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/secured/SecuredContextPathTests.java) |
| `protocol: SSE` | [`SecuredSseTests`](../samples/secured-demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/secured/SecuredSseTests.java) |
| `protocol: STATELESS` | [`SecuredStatelessTests`](../samples/secured-demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/secured/SecuredStatelessTests.java) |
| Every class, field and method boot-mcp-doctor uses exists on this Spring Boot version, with every optional dependency present | [`LinkageTests`](../samples/secured-demo-app/src/test/java/com/nhparikh/repo/bootmcpdoctor/secured/LinkageTests.java) |

To run a sample yourself:

```bash
./mvnw install -DskipTests
./mvnw -pl samples/secured-demo-app spring-boot:run
```

The secured sample's users are `ops`, `admin` and `viewer`, with the password set in `demo.password` in its `application.yml`. Swap in `samples/demo-app` for the sample without Spring Security.

## FAQ

**Do I need to expose Actuator endpoints over HTTP?**

No. The tools read the application in-process. `get_health` uses Actuator's health endpoint when Spring Boot has created it and otherwise reads the health contributors directly, so exposure settings make no difference. Access settings (`management.endpoint.<id>.access`) do apply to every tool.

**Does the management port matter?**

No. The MCP endpoint is served on the application's main port, alongside your own endpoints, even when `management.server.port` moves Actuator elsewhere.

**Can I have my own MCP tools as well?**

Yes. Annotate them with `@McpTool` and leave Spring AI's annotation scanner on. boot-mcp-doctor registers its tools separately, and the client sees both.

**Does it slow the application down?**

Tools only run when a client calls them, and each call reads what is already in memory. The connection log only looks at POST requests to the MCP endpoint.

**Is anything stored or sent somewhere?**

No. Each call reads live values and returns them to the caller only.

**Which assistant does it work with?**

Any MCP client that supports Streamable HTTP, such as GitHub Copilot in VS Code and JetBrains IDEs, or, through `mcp-remote`, clients that only start local processes.
