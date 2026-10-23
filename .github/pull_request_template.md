## Summary

<!-- What does this change, and why? Link the issue it addresses, for example "Closes #12". -->

## Type of change

- [ ] Bug fix
- [ ] New tool
- [ ] Improvement to an existing tool
- [ ] Configuration or safety
- [ ] Documentation
- [ ] Build or CI

## Safety

<!--
Does this add or change anything that modifies the running application, or that could return more data than before?
If so, explain how read-only mode, Actuator access settings, masking and response limits still apply.
Write "No change" if it doesn't affect any of these.
-->

## Testing

<!-- How did you verify this? List the tests you added and anything you tried by hand, such as calls against the demo app. -->

## Checklist

- [ ] `./mvnw verify` passes locally, including the 85% coverage check
- [ ] New behavior has tests, including a masking test if the output can contain configuration values
- [ ] Public API, tool descriptions and properties are documented
- [ ] The README is updated if usage changed
- [ ] No new dependencies, or the reason is explained above
- [ ] No secrets, internal hostnames, customer data, or code I don't have the right to share
