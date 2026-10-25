# Releasing

Releases are published to Maven Central under the `com.nhparikh.repo` namespace.

The [release workflow](.github/workflows/release.yml) does the whole release from one click. It checks that the version is new and that CI passed, waits for your approval, then builds, tests, signs and uploads to Maven Central, publishes, and creates the `vX.Y.Z` tag and GitHub release. Each module's CycloneDX SBOM goes to Maven Central, and the starter's SBOM is attached to the GitHub release. The workflow never pushes commits: the version comes from `pom.xml`, so moving `main` to the next version is a normal pull request.

## One-time setup

1. **Central Portal account and namespace.** Sign in at [central.sonatype.com](https://central.sonatype.com) and add the `com.nhparikh.repo` namespace under *Namespaces*. Central verifies it through a DNS TXT record on `nhparikh.com`; the namespace must show as verified before anything can be published.

2. **Publishing token.** In the portal, open *View Account* → *Generate User Token*. Keep the token username and password for step 4.

3. **Signing key.** Create an RSA 4096 key that is only used for releases, separate from the key you sign commits with, and publish its public half so Maven Central can verify signatures:

   ```bash
   gpg --quick-gen-key "Nihar Parikh (Maven Central releases) <nihar17999@gmail.com>" rsa4096 sign 3y
   gpg --list-secret-keys --keyid-format long "Maven Central releases"
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```

4. **GitHub environment and secrets.** Create an environment named `maven-central` under *Settings* → *Environments*:
   - **Required reviewers:** add yourself, so every release waits for your approval before anything is uploaded. Leave *Prevent self-review* off.
   - **Deployment branches and tags:** *Selected branches and tags* → add a **Branch** rule for `main`, so only runs from `main` can use the secrets.

   Then store the secrets in it; each `gh secret set` prompts for the value:

   ```bash
   gh secret set MAVEN_CENTRAL_USERNAME --env maven-central
   gh secret set MAVEN_CENTRAL_PASSWORD --env maven-central
   gpg --armor --export-secret-keys <KEY_ID> | gh secret set MAVEN_GPG_PRIVATE_KEY --env maven-central
   gh secret set MAVEN_GPG_PASSPHRASE --env maven-central
   ```

## Releasing a version

1. Make sure `pom.xml` on `main` has the version you want, as `X.Y.Z-SNAPSHOT`, and that CI passed on it. `main` also needs, for that version:
   - **A migration guide**, `docs/migrations/X.Y.Z.md`, written from [the template](docs/migrations/TEMPLATE.md) and listed in [the index](docs/migrations/README.md). Every release gets one, even when it only says that no migration is needed, and it must describe the release as it ships. The release workflow stops without it, and the GitHub release links to it.
   - **The compatibility chart** in [docs/compatibility.md](docs/compatibility.md), updated with the Spring Boot and Spring AI versions the release was tested on, and what it can and cannot do on each. `scripts/compatibility.sh` runs both samples on every supported pair; `scripts/compatibility.sh 6` uses six workers. If the supported Spring Boot or Spring AI line changes, also change `SPRING_BOOT` and `SPRING_AI` in `CompatibilityLog`, which logs them at startup; a unit test fails when they don't match the versions the build uses.
2. Optionally, do a dry run first. It builds, tests and signs the bundle with the real key, but uploads nothing:

   ```bash
   gh workflow run release.yml --repo NiharParikh17/boot-mcp-doctor -f dry_run=true
   ```

3. Release. From the browser: *Actions* → *Release* → *Run workflow*, leaving *version* empty and *publish* ticked. Or:

   ```bash
   gh workflow run release.yml --repo NiharParikh17/boot-mcp-doctor
   ```

4. Approve it. The run stops at the `maven-central` environment; open it and choose *Review deployments* → *Approve*. Publishing is the one step that can never be undone, so this is the moment to check the version and commit it shows.
5. Wait for the run to finish. It waits until Maven Central has published the version, usually within 30 minutes, then creates the tag and GitHub release.
6. In a pull request, move `main` to the next version and update the version in `README.md`:

   ```bash
   ./mvnw versions:set -DnewVersion=0.2.0-SNAPSHOT -DprocessAllModules -DgenerateBackupPoms=false
   ```

To release a different version than `pom.xml` says, type it into *version*. To stop before publishing, untick *publish*: the upload stops once Central has validated it, and the GitHub release is created as a draft. Publish the deployment on [central.sonatype.com](https://central.sonatype.com/publishing/deployments), then publish the draft release.

If a run fails before publishing, nothing is released; fix the problem and run it again. A version that reached Maven Central can never be replaced, so a fix after that is a new patch version.

## Checking a release locally

The CI job *Check release bundle* builds the same bundle on every pull request. To build it locally without signing or uploading:

```bash
./mvnw -P release -DskipTests -Dgpg.skip -DskipUpload deploy
```

For a `-SNAPSHOT` version this stages snapshot artifacts instead of `target/central-publishing/central-bundle.zip`, so set a release version first (in a scratch copy) to see the real bundle.
