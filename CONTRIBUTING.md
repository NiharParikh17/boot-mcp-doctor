# Contributing

Thanks for your interest in boot-mcp-doctor. Bug reports, ideas, documentation fixes and code are all welcome.

Everyone taking part in the project is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to contribute

- **Report a bug or ask a question:** [open an issue](https://github.com/NiharParikh17/boot-mcp-doctor/issues/new/choose) using one of the templates.
- **Suggest a feature:** use the feature request template. Starting from a real situation (what you wanted to know about a running application) helps most.
- **Report a vulnerability:** don't open an issue. Follow [SECURITY.md](SECURITY.md) instead.
- **Send a pull request:** small fixes can go straight to a pull request. For a new tool, or anything that changes application state, adds a dependency or changes a default, please open an issue first so we can agree on the approach before you spend time on it.

## Development setup

You need Java 17 or later. CI builds on JDK 17, 21 and 25. Maven comes with the repository through the wrapper.

```bash
./mvnw verify
```

`verify` compiles everything, runs the tests (including an end-to-end test that calls the tools through a real MCP client), and fails if line coverage of `boot-mcp-doctor-autoconfigure` drops below 85%. The coverage report is written to `boot-mcp-doctor-autoconfigure/target/site/jacoco/index.html`.

To try your change against the sample application:

```bash
./mvnw install -DskipTests
./mvnw -pl samples/demo-app spring-boot:run
```

Then point an MCP client at `http://localhost:8080/mcp`.

## Project layout

| Module | Contents |
|---|---|
| `boot-mcp-doctor-autoconfigure` | All of the code: auto-configuration, properties, tools, response records and the safety layer |
| `boot-mcp-doctor-spring-boot-starter` | No code; pulls in the auto-configuration, Actuator and the Spring MVC MCP server |
| `samples/demo-app` | A sample order service with a slow endpoint and a failing health check; never published |

Inside the auto-configuration module, under `com.nhparikh.repo.bootmcpdoctor`:

- `autoconfigure`: `BootMcpDoctorAutoConfiguration` and `ToolSelector`, which decides which tools are registered
- `config`: the `boot-mcp-doctor.*` properties
- `tools`: one class per tool group, such as `HealthTools` or `MetricsTools`
- `model`: the records each tool returns
- `safety`: `ResponseSanitizer`, which masks secrets and caps response sizes

## Adding or changing a tool

boot-mcp-doctor is read-only and safe by default. Every tool needs to keep it that way.

- **Read in-process.** Use the application's own beans, such as the meter registry, environment or logging system, through an `ObjectProvider`. Never call the application over HTTP.
- **Degrade gracefully.** If the data source is missing, return a clear message (for example, "Metrics not available. Is micrometer on the classpath?") rather than throwing.
- **Pass every response through `ResponseSanitizer`.** Mask values whose keys look sensitive, and cap every list and map at `limits.max-items`.
- **Return small, summarized records.** Add a record to `model`, sort the most important entries first (failures, slowest, largest), include units such as `ms` or `MB`, and add a one-line `summary`. Never return raw Actuator payloads or full dumps.
- **Write the description for whoever calls the tool.** Say what the tool does, when to use it and how to read its output. Set the `@McpTool` annotations: `readOnlyHint = true` for read tools.
- **Register it through a `ToolGroup`.** Add a `ToolGroup` bean in `BootMcpDoctorAutoConfiguration` with the matching Actuator endpoint ID, so Actuator access settings apply. Guard it with `@ConditionalOnClass` if it needs an optional module. Tool classes are not beans themselves.
- **Treat anything that changes state as a write tool.** Mark the group with `write = true` and set `readOnlyHint = false`. Write tools are only registered when `boot-mcp-doctor.read-only=false`, Spring Security is present, `boot-mcp-doctor.security.write-authority` is set and the endpoint's Actuator access allows it, and every call needs that authority. `ToolGuard` checks each call, so a new tool needs no security code of its own.
- **Document it.** Add the tool to the Tools table in the README, and any new property to the Configuration table.

## Tests

Every change needs tests. For a tool, that usually means:

- unit tests for the normal case, an empty result, a missing data source and output larger than `max-items`;
- a masking test whenever the output can contain configuration values or other secrets, asserting that the secret never appears;
- an auto-configuration test showing when the tool is and isn't registered;
- a call from `DemoApplicationTests` if the tool works against the sample application.

## Code style

There's no automatic formatter yet, so please match the surrounding code:

- 4-space indentation and lines of at most 120 characters
- imports grouped as `java.*`, third-party, `org.springframework.*`, then static imports
- constructor injection only; records for data; no Lombok
- Javadoc on public classes, methods and properties; keep internal classes package-private where possible
- one tool group per class, kept small

Please don't add a dependency without explaining why in the pull request. New dependencies must be licensed under Apache-2.0, MIT, MIT-0, BSD-2-Clause, BSD-3-Clause, ISC, EPL-1.0, EPL-2.0, CC0-1.0 or a Unicode license; a dual-licensed package passes if one of its options is on that list. The *Review dependency changes* check enforces this, and also fails when a new dependency has a known vulnerability of moderate severity or higher.

## Commits and pull requests

- Work on a branch with a descriptive name, such as `add-thread-dump-summary`.
- Keep commits focused, and write messages in the imperative mood: "Add get_threads tool", not "Added get_threads tool".
- Fill in the pull request template. CI must pass on every pull request.
- Only contribute code you have the right to share, and never include secrets, internal hostnames or customer data, even in tests.

## License

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE), the same license as the project.
