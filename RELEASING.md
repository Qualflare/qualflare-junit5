# Releasing

## The thing to internalise first

**Maven Central is immutable.** A published version can never be edited, replaced or
withdrawn — only superseded by a higher one. This is not like npm, where a bad publish can
be deprecated and a fixed one shipped under the same intent, and it is not like a container
tag you can move. A wrong `0.1.0` is `0.1.0` forever.

That is why `autoPublish` is `false` and why the gate runs in full before anything uploads.

## Cutting a release

1. Make sure `main` is green — CI covers Java 11/17/21 and the fixture against JUnit 5.12,
   5.13 and 6.x.

2. Set the version. Central rejects `-SNAPSHOT`, and the release workflow refuses it too:

   ```bash
   mvn versions:set -DnewVersion=0.1.0 -DgenerateBackupPoms=false
   ```

3. Update `CHANGELOG.md`.

4. Commit and tag. The tag must match the pom version exactly; the workflow asserts it.

   ```bash
   git commit -am "chore: release v0.1.0"
   git tag v0.1.0 && git push origin main v0.1.0
   ```

5. The workflow runs the gate, signs, and uploads a **validated but unpublished** bundle.

6. **Publish it by hand** at
   [central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments).
   Look at what you are about to make permanent first: the coordinates, the version, and
   that the sources and javadoc jars are present.

7. Verify at the source, never at a green checkmark:

   ```bash
   # the artifact itself, not a dashboard
   curl -sI https://repo1.maven.org/maven2/com/qualflare/qualflare-junit5/0.1.0/qualflare-junit5-0.1.0.jar
   ```

   Propagation to `repo1` takes a few minutes and the search index takes longer — an
   artifact missing from search is not yet a failed release.

   A green workflow is not evidence of a publish. `@qualflare/cli` once reported
   `OK Test results collected successfully` for eleven consecutive runs while nothing
   reached the server.

8. Bump to the next `-SNAPSHOT` and commit.

## Secrets

Four repository secrets, all set through the Portal and GPG rather than generated here:

| secret | source |
|---|---|
| `CENTRAL_TOKEN_USERNAME` | Central Portal user token — **not** the login |
| `CENTRAL_TOKEN_PASSWORD` | Central Portal user token |
| `GPG_PRIVATE_KEY` | `gpg --armor --export-secret-keys <fingerprint>` |
| `GPG_PASSPHRASE` | the passphrase on that key |

## Notes on the signing key

- **The signature must come from the PRIMARY key.** Central verifies against the primary
  only, so a separate `[S]` signing subkey would be rejected. The current key is
  `ed25519 [SC]` with an encrypt-only `cv25519 [E]` subkey, so the primary is the only
  signer and this is satisfied by construction.
- **EdDSA is supported.** Central's accepted list is RSA, ELG, DSA, ECDH, ECDSA, EDDSA.
- **The public key must be on a keyserver Central actually checks**: `keyserver.ubuntu.com`,
  `keys.openpgp.org` or `pgp.mit.edu`. Others will not be consulted.
- **The key expires 2029-09-10.** Signatures made before then stay valid, so nothing breaks
  retroactively — but publishing after that date needs the key extended or rotated.

## When to turn autoPublish on

Once a release has gone through cleanly a couple of times and the manual step has stopped
catching anything. Set `<autoPublish>true</autoPublish>` in the `release` profile. Keep
`waitUntil` at `validated` regardless, so a malformed bundle fails the workflow rather than
sitting silently in the Portal.

## Version policy

Semver. While `0.x`, breaking changes ride a minor bump.

The floor is **JUnit Platform 1.12 / Jupiter 5.12 and Java 11**, and raising either is a
breaking change: 1.12 is where `fileEntryPublished` first exists, which is the callback
attachments arrive on.
