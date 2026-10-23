# Security policy

## Reporting a vulnerability

Please don't report security problems in public issues, pull requests or discussions.

Report them privately through GitHub instead: open the [Security tab](https://github.com/NiharParikh17/boot-mcp-doctor/security) and choose **Report a vulnerability**. Include the version, your configuration (with secrets removed) and the steps or tool calls that show the problem. You'll get a reply in the private advisory, and a fix is released before the details are made public.

## Supported versions

Fixes go into the latest release. Until 0.1.0 is published, that means `main`.

## Dependencies

Dependabot alerts watch every dependency on `main` for newly published vulnerabilities. CI submits the fully resolved Maven tree to GitHub so that versions managed by the Spring Boot and Spring AI BOMs are covered too. A pull request that adds a dependency with a known vulnerability of moderate severity or higher fails the *Review dependency changes* check.

Vulnerable dependencies are fixed by upgrading, usually to the latest Spring Boot or Spring AI patch release, followed by a patch release of boot-mcp-doctor when the published artifacts are affected. Most dependencies are managed by Spring Boot, so an application normally runs the versions from its own Spring Boot version; keeping that on its latest patch release matters most.

Every release includes a CycloneDX SBOM listing each dependency and its license. It is attached to the GitHub release and published to Maven Central next to each artifact with the `cyclonedx` classifier.

## What counts as a vulnerability

boot-mcp-doctor's job is to describe a running application without leaking secrets or changing it unexpectedly. Examples of issues to report privately:

- A secret appears unmasked in any tool response.
- A tool call succeeds without an authenticated caller, or without the configured `boot-mcp-doctor.security.read-authority` or `write-authority`, while Spring Security is on the classpath.
- Tools are registered without Spring Security while `boot-mcp-doctor.security.allow-unauthenticated=false`.
- A tool that changes state, such as `set_log_level`, is registered while `boot-mcp-doctor.read-only=true`, without Spring Security, before `security.write-authority` is set, or while the endpoint's Actuator access is `none` or `read-only`.
- A tool is registered that `boot-mcp-doctor.tools.exclude` or Actuator access should have removed.
- A response ignores `boot-mcp-doctor.limits.max-items` in a way that exposes more data than intended.

How callers authenticate is up to the application's Spring Security configuration. Running with `boot-mcp-doctor.security.allow-unauthenticated=true` where untrusted clients can reach the endpoint is a deployment issue rather than a vulnerability; see the Security section of the [README](README.md#security).
