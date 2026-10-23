# Security policy

## Reporting a vulnerability

Please don't report security problems in public issues, pull requests or discussions.

Report them privately through GitHub instead: open the [Security tab](https://github.com/NiharParikh17/boot-mcp-doctor/security) and choose **Report a vulnerability**. Include the version, your configuration (with secrets removed) and the steps or tool calls that show the problem. You'll get a reply in the private advisory, and a fix is released before the details are made public.

## Supported versions

Fixes go into the latest release. Until 0.1.0 is published, that means `main`.

## What counts as a vulnerability

boot-mcp-doctor's job is to describe a running application without leaking secrets or changing it unexpectedly. Examples of issues to report privately:

- A secret appears unmasked in any tool response.
- A tool that changes state, such as `set_log_level`, is registered while `boot-mcp-doctor.read-only=true`, or while the endpoint's Actuator access is `none` or `read-only`.
- A tool is registered that `boot-mcp-doctor.tools.exclude` or Actuator access should have removed.
- A response ignores `boot-mcp-doctor.limits.max-items` in a way that exposes more data than intended.

The MCP endpoint has no authentication of its own. Leaving it reachable from untrusted networks is a deployment issue rather than a vulnerability; see the Security section of the [README](README.md#security).
