# Releasing

Releases are published to Maven Central under the `io.github.niharparikh17` namespace.

Publishing a GitHub release tagged `vX.Y.Z` runs the [release workflow](.github/workflows/release.yml), which builds, tests, signs and uploads that version. Each module's CycloneDX SBOM goes to Maven Central with it, and the starter's SBOM is also attached to the GitHub release. The upload stops once Maven Central has validated it. The final publish is a manual click on central.sonatype.com, because a published version can never be changed or removed.

## One-time setup

1. **Central Portal account.** Sign in at [central.sonatype.com](https://central.sonatype.com) with your GitHub account. The `io.github.niharparikh17` namespace is verified automatically for GitHub sign-ins; check it under *Namespaces*.

2. **Publishing token.** In the portal, open *View Account* → *Generate User Token*. Keep the token username and password for step 4.

3. **Signing key.** Create a key that is only used for releases, and publish its public half so Maven Central can verify signatures:

   ```bash
   gpg --quick-gen-key "Nihar Parikh <nihar17999@gmail.com>" ed25519 sign 3y
   gpg --list-secret-keys --keyid-format long
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```

4. **GitHub environment and secrets.** Create an environment named `maven-central` under *Settings* → *Environments*. Adding yourself as a required reviewer makes every release wait for your approval. Then store the secrets in it; each `gh secret set` prompts for the value:

   ```bash
   gh secret set MAVEN_CENTRAL_USERNAME --env maven-central
   gh secret set MAVEN_CENTRAL_PASSWORD --env maven-central
   gpg --armor --export-secret-keys <KEY_ID> | gh secret set MAVEN_GPG_PRIVATE_KEY --env maven-central
   gh secret set MAVEN_GPG_PASSPHRASE --env maven-central
   ```

## Releasing a version

1. Check that CI is green on `main`.
2. Create the release. The tag sets the version; the POMs on `main` keep their `-SNAPSHOT` version.

   ```bash
   gh release create v0.1.0 --target main --title v0.1.0 --generate-notes
   ```

3. Approve the `maven-central` deployment in the workflow run if you added a required reviewer, and wait for the run to finish.
4. Open [Deployments](https://central.sonatype.com/publishing/deployments), check the files, and click **Publish**. The version appears on Maven Central shortly after.
5. Move `main` to the next development version, for example `0.2.0-SNAPSHOT`:

   ```bash
   ./mvnw versions:set -DnewVersion=0.2.0-SNAPSHOT -DprocessAllModules -DgenerateBackupPoms=false
   ```

## Checking a release locally

The CI job *Check release bundle* builds the same bundle on every pull request. To build it locally without signing or uploading:

```bash
./mvnw -P release -DskipTests -Dgpg.skip -DskipUpload deploy
```

For a `-SNAPSHOT` version this stages snapshot artifacts instead of `target/central-publishing/central-bundle.zip`, so set a release version first (in a scratch copy) to see the real bundle.
