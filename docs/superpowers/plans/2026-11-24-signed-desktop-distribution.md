# Signed Mac Installers and Linux Package Repositories Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Developer ID–signed, notarized DMG/PKG (with in-app DMG updates via `hammer.zip`), a signed RPM, and apt/dnf repos on Cloudsmith fed only by published releases, plus a hammer.ink Linux install page.

**Architecture:** A new `linux-repo` distribution channel marks repo builds so the existing updater gate turns in-app updates off. A reusable workflow builds and signs the repo packages as a workflow artifact during `prepare-release.yml`; a publish workflow fetches that artifact on release publish and pushes it to Cloudsmith, with a manual rebuild-from-tag escape hatch. macOS signing identities move into one testable buildSrc function shared by the Compose DSL and `signMacAppResources`. Every new signing/publishing step is skipped with a warning when its secret is absent.

**Tech Stack:** Kotlin (buildSrc, Compose Desktop, Ktor server + Mustache), GitHub Actions, `codesign`/`notarytool`/`stapler`, `rpmsign`/GnuPG, Cloudsmith CLI, Nucleus updater 2.5.18.

**Spec:** `docs/superpowers/specs/2026-11-24-signed-desktop-distribution-design.md`

## Global Constraints

- Channel token `linux-repo`, `DistributionChannel` display name `Linux package repository`; `Platform` token `linux-repo`, display name `Linux repos`.
- Repo packages: package name `hammer`, **same version** as the GitHub packages (no revision bump, no epoch).
- No Windows changes. Mac App Store signing (identity `3rd Party Mac Developer Application: Adam Brown (8P3G3HT4J5)`, `entitlements.plist`, `runtime-entitlements.plist`, provisioning profiles, JNA jvmArgs) must behave exactly as today.
- Developer ID identities: `Developer ID Application: Adam Brown (8P3G3HT4J5)` and `Developer ID Installer: Adam Brown (8P3G3HT4J5)`; Compose `identity` value `Adam Brown`.
- Notarization reuses the existing App Store Connect key: key id `F8BSZ7LC8Q`, issuer `a49b927f-0f0e-4d4c-85c6-ffddfac9125a`, secret `MAC_APP_STORE_CONNECT_API_KEY_P8`.
- New secrets (exact names): `MAC_DEVELOPER_ID_APP_P12_BASE64`, `MAC_DEVELOPER_ID_INSTALLER_P12_BASE64`, `MAC_DEVELOPER_ID_CERT_PASSWORD`, `LINUX_GPG_PRIVATE_KEY` (ASCII-armored), `LINUX_GPG_PASSPHRASE`, `CLOUDSMITH_API_KEY`. New repository **variables**: `CLOUDSMITH_OWNER`, `CLOUDSMITH_REPO` (one Cloudsmith repo holds both formats; distribution `any-distro/any-version`).
- Secret gating: expose `HAS_<X>: ${{ secrets.<X> != '' }}` in job-level `env`; a gated step runs `if: env.HAS_<X> == 'true'`, and a companion step with the opposite condition prints `::warning::<what> skipped: <SECRET> is not set`. When the secret is set and the step fails, the job fails.
- One Gradle packaging task per invocation (DEVELOPMENT.md: two in one invocation share the first `-Pformat`).
- `writeAssetDigests` runs only after the final signing/notarizing/stapling of each file.
- Workflow artifact name `linux-repo-packages`, `retention-days: 90`, containing exactly `hammer.deb` and `hammer.rpm`.
- Public URLs: site `https://hammer.ink`, repo `https://packages.hammer.ink` (Cloudflare redirect to `https://dl.cloudsmith.io/public/<CLOUDSMITH_OWNER>/<CLOUDSMITH_REPO>`).
- Task 9 (updater switches DMG to `hammer.zip`) merges only after the Developer ID secrets are configured. Until then a DMG build would look for a zip that no release carries.

## Review Focus

1. **Subset tags such as `v4.0.1+linux-repo`.** The publish job must strip the `+…` suffix before comparing package versions, and must URL-encode the tag when looking up the run. Covered by Task 3, Step 2.
2. **An auto-published release.** The publish starts while its `prepare-release` run is still in progress, so the lookup must not filter on the run's status. Covered by Task 3, Step 2: the lookup keys on whether the artifact exists.
3. **Re-running a publish for a version already on Cloudsmith.** It must succeed, not fail on a duplicate. Covered by Task 3, Step 3 (`--republish`).
4. **An unsigned RPM reaching the repo.** This happens when `LINUX_GPG_*` is missing but `CLOUDSMITH_API_KEY` is set. The publish must refuse. Covered by Task 3, Step 2's signature check.
5. **A `hammer.zip` that isn't actually notarized, or that carries the PKG build of the app.** Users would get an app Gatekeeper blocks, or one that reports the wrong format. Covered by Task 7, Steps 3–4 (`stapler validate`, `spctl`, and zipping before the PKG run).

---

### Task 1: `linux-repo` channel and platform token

**Files:**
- Modify: `base/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/base/DistributionChannel.kt`
- Modify: `buildSrc/src/main/java/distributionChannel.kt`
- Modify: `buildSrc/src/main/java/platforms.kt`
- Modify: `.github/scripts/discord-release-message.sh`
- Modify: `DEVELOPMENT.md` (channel/format section, ~line 170)
- Test: `base/src/commonTest/kotlin/com/darkrockstudios/apps/hammer/base/DistributionChannelTest.kt`, `buildSrc/src/test/java/PlatformsTest.kt`, `desktop/src/jvmTest/kotlin/com/darkrockstudios/apps/hammer/desktop/update/UpdaterSupportTest.kt`

**Interfaces:**
- Produces: `DistributionChannel.LINUX_REPO` (token `linux-repo`) in both enums; `Platform.LINUX_REPO` (token `linux-repo`, a member of `CLIENT_STORES`).

- [ ] **Step 1: Write the failing tests**
  - `base` `DistributionChannelTest.tokens match the build side enum`: append `"linux-repo"` after `"ios-app-store"` in `expected`.
  - `PlatformsTest`: new test `` `Linux repos is a client store with its own token` `` asserting `tagSuffix(setOf(Platform.LINUX_REPO)) == "+linux-repo"` and `Platform.LINUX_REPO in Platform.CLIENT_STORES`.
  - `UpdaterSupportTest`: new test `` `repo installs never update themselves` `` asserting `assertSame(NoOpAppUpdater, create(DistributionChannel.LINUX_REPO, PackageFormat.DEB))` and the same for `RPM`.

- [ ] **Step 2: Run them and see them fail**
  Run: `./gradlew :base:desktopTest --tests '*DistributionChannelTest*' && ./gradlew -p buildSrc test --tests '*PlatformsTest*'`
  Expected: compile failure / assertion failure on the missing `LINUX_REPO`.

- [ ] **Step 3: Add the enum entries**
  `LINUX_REPO("linux-repo", "Linux package repository")` as the last entry of the `:base` enum, `LINUX_REPO("linux-repo")` as the last entry of the buildSrc channel enum, `LINUX_REPO("linux-repo", "Linux repos")` in `Platform` after `IOS_APP_STORE` and before `SERVER`. Add `linux-repo) echo "Linux repos" ;;` to `to_display()` in the Discord script. Grep every `when` on `DistributionChannel` (`grep -rn "DistributionChannel\." --include=*.kt`). None is exhaustive today, but if one has become exhaustive, give `LINUX_REPO` the `GITHUB` answer. In DEVELOPMENT.md add one sentence saying that `linux-repo` marks the apt/dnf builds and never self-updates.

- [ ] **Step 4: Run the tests and see them pass**
  Run: `./gradlew :base:desktopTest && ./gradlew -p buildSrc test && ./gradlew :desktop:jvmTest --tests '*UpdaterSupportTest*'`
  Expected: PASS (the existing `only the direct download channels may update` test still passes unchanged).

- [ ] **Step 5: Commit**
  `git commit -m "Add the linux-repo distribution channel and release token"`

---

### Task 2: Build and sign the repo packages in the release run

**Files:**
- Create: `.github/workflows/build-linux-repo.yml` (reusable)
- Create: `desktop/scripts/sign-rpm.sh`
- Modify: `.github/workflows/prepare-release.yml` (`linux` job: sign `hammer.rpm`; new `linux-repo` job; `set-release-body.needs`)

**Interfaces:**
- Consumes: channel token `linux-repo` (Task 1).
- Produces: `build-linux-repo.yml` with `on.workflow_call.inputs.ref` (string, required) and one job named `build`, which uploads artifact `linux-repo-packages` (`hammer.deb`, `hammer.rpm`) as its **last** step. `desktop/scripts/sign-rpm.sh <rpm-path>` reads env `LINUX_GPG_PRIVATE_KEY` and `LINUX_GPG_PASSPHRASE`, imports the key into a temporary `GNUPGHOME`, runs `rpmsign --addsign` with `_gpg_name` set to the imported key's fingerprint and loopback pinentry, then `rpm -Kv` and fails unless the output contains `Signature` … `OK` once the key is imported into a temporary rpmdb. Exit code 0 means the file is signed.

- [ ] **Step 1: Write `sign-rpm.sh`**, `set -euo pipefail`, temp dirs cleaned up on exit with `trap`, and the passphrase never echoed.

- [ ] **Step 2: Verify `sign-rpm.sh` locally with a throwaway key**
  Run: `gpg --batch --passphrase test --quick-gen-key 'Test <t@example.com>' rsa4096 sign never` in a scratch `GNUPGHOME`, export it armored into `LINUX_GPG_PRIVATE_KEY`, then `./gradlew :desktop:packageReleaseRpm -Pchannel=linux-repo -Pformat=rpm` and `desktop/scripts/sign-rpm.sh desktop/build/installers/main-release/rpm/hammer-*.rpm`.
  Expected: exit 0, and `rpm -qp --qf '%{RSAHEADER:pgpsig}\n'` prints `RSA/SHA256, …, Key ID …` rather than `(none)`.

- [ ] **Step 3: Write `build-linux-repo.yml`**. Check out `inputs.ref`, set up JDK 21 (same as the `linux` job), install `rpm`. Then `./gradlew :desktop:packageReleaseDeb -Pchannel=linux-repo -Pformat=deb`, then a **separate** `./gradlew :desktop:packageReleaseRpm -Pchannel=linux-repo -Pformat=rpm`. Copy the outputs to `out/hammer.deb` and `out/hammer.rpm`. If `HAS_LINUX_GPG_PRIVATE_KEY` is true, run `sign-rpm.sh out/hammer.rpm`; otherwise print the warning. Finally `actions/upload-artifact` with name `linux-repo-packages`, path `out/`, `retention-days: 90`, `if-no-files-found: error`. Declare `secrets: LINUX_GPG_PRIVATE_KEY, LINUX_GPG_PASSPHRASE` as optional `workflow_call` secrets.

- [ ] **Step 4: Wire into `prepare-release.yml`**
  - New job `linux-repo: { needs: tests, uses: ./.github/workflows/build-linux-repo.yml, with: { ref: ${{ github.ref }} }, secrets: inherit }`.
  - Add `linux-repo` to `set-release-body.needs`.
  - In the `linux` job, after "Rename RPM Artifact" and before "Write update digests", add the gated `sign-rpm.sh desktop/build/installers/main-release/rpm/hammer.rpm` step (and its warning twin). Install `rpm` there if the runner lacks `rpmsign`.

- [ ] **Step 5: Verify the workflows parse**
  Run: `pipx run actionlint .github/workflows/build-linux-repo.yml .github/workflows/prepare-release.yml` (or `actionlint` if installed).
  Expected: no errors.

- [ ] **Step 6: Commit**
  `git commit -m "Build and sign the linux-repo packages in the release run"`

---

### Task 3: Publish the repo packages to Cloudsmith

**Files:**
- Create: `.github/workflows/publish-linux-repo.yml`
- Create: `.github/scripts/fetch-linux-repo-packages.sh`
- Modify: `.github/workflows/publish-release.yml`

**Interfaces:**
- Consumes: artifact `linux-repo-packages` and reusable `build-linux-repo.yml` (Task 2).
- Produces: `publish-linux-repo.yml` with `workflow_call` input `release_tag` (string, required) and `workflow_dispatch` inputs `release_tag` (string, required), `rebuild_from_tag` (boolean, default false), `dry_run` (boolean, default false). `fetch-linux-repo-packages.sh <tag> <dest-dir>` exits 0 with `<dest-dir>/hammer.deb` and `<dest-dir>/hammer.rpm`, or exits 1 with the message in Step 2.

- [ ] **Step 1: Write `fetch-linux-repo-packages.sh`**. List the `prepare-release.yml` runs for the tag, newest first: `gh api -X GET "repos/$GITHUB_REPOSITORY/actions/workflows/prepare-release.yml/runs" -f event=push -f branch="$TAG"` (the `-f` form URL-encodes the `+`). Do **not** filter on status. For each run, try `gh run download <id> -n linux-repo-packages -D <dest>` and stop at the first success. If none succeeds, `echo "::error::..."` with this exact recovery text: `No linux-repo-packages artifact for $TAG (missing, failed, or expired after 90 days). Re-run "Publish — Linux repos" manually with release_tag=$TAG and rebuild_from_tag=true.` Then exit 1.

- [ ] **Step 2: Write `publish-linux-repo.yml`** with these jobs:
  - `rebuild` (only `if: inputs.rebuild_from_tag`): `uses: ./.github/workflows/build-linux-repo.yml` with `ref: refs/tags/${{ inputs.release_tag }}`, `secrets: inherit`.
  - `publish` (`needs: rebuild`, `if: !cancelled() && needs.rebuild.result != 'failure'`). Job env `HAS_CLOUDSMITH_API_KEY`. If not set: warning, then stop with success. Otherwise get the packages: with `rebuild_from_tag`, `actions/download-artifact` from this run; else `fetch-linux-repo-packages.sh`. Then **verify**:
    - `VERSION="${TAG#v}"; VERSION="${VERSION%%+*}"`.
    - `dpkg-deb -f hammer.deb Version` and `rpm -qp --qf '%{VERSION}' hammer.rpm` must each equal `$VERSION` or start with `$VERSION-`.
    - `dpkg-deb -f hammer.deb Package` and `rpm -qp --qf '%{NAME}' hammer.rpm` must both be `hammer`.
    - `rpm -qp --qf '%{RSAHEADER:pgpsig}' hammer.rpm` must not be `(none)` (Review Focus 4).

    Any mismatch: `::error::` and exit 1.

- [ ] **Step 3: Push to Cloudsmith** (skipped when `dry_run`, which prints what it would push instead): `pip install cloudsmith-cli==<pin latest>`, then `cloudsmith push deb "$CLOUDSMITH_OWNER/$CLOUDSMITH_REPO/any-distro/any-version" hammer.deb --republish` and `cloudsmith push rpm "$CLOUDSMITH_OWNER/$CLOUDSMITH_REPO/any-distro/any-version" hammer.rpm --republish`, with `CLOUDSMITH_API_KEY` in env and owner and repo from `vars`.

- [ ] **Step 4: Wire into `publish-release.yml`**: job `publish-linux-repo` with `if: ${{ !contains(github.event.release.tag_name, '+') || contains(github.event.release.tag_name, '+linux-repo') }}`, `uses: ./.github/workflows/publish-linux-repo.yml`, `secrets: inherit`, `with: { release_tag: ${{ github.event.release.tag_name }} }`, `permissions: { contents: read, actions: read }`. Add it to `notify.needs`.

- [ ] **Step 5: Verify**
  Run: `actionlint` on the three workflows, then `bash -n .github/scripts/fetch-linux-repo-packages.sh`.
  Expected: clean. After merge, run a `workflow_dispatch` with `release_tag=<latest tag that has the artifact>` and `dry_run=true`. Expected: the version, name and signature checks pass and the would-push lines are printed.

- [ ] **Step 6: Commit**
  `git commit -m "Publish linux-repo packages to Cloudsmith on release, with a manual rebuild path"`

---

### Task 4: Linux install page on hammer.ink

**Files:**
- Create: `server/src/main/kotlin/com/darkrockstudios/apps/hammer/frontend/LinuxInstallPage.kt`
- Create: `server/src/main/resources/templates/linux-install.mustache`
- Create: `server/src/main/resources/assets/css/linux-install.css`, `server/src/main/resources/assets/js/linux-install.js`
- Modify: `server/src/main/kotlin/com/darkrockstudios/apps/hammer/frontend/Frontend.kt:129` (register route)
- Modify: `server/src/main/resources/templates/home.mustache:374-400` (Linux card link)
- Modify: `server/src/main/resources/i18n/Messages_en.properties` (only the English bundle; Crowdin owns the rest)
- Modify: sitemap builder used by `SitemapRoutesTest` (always include `/download/linux`)
- Test: `server/src/test/kotlin/com/darkrockstudios/apps/hammer/frontend/LinuxInstallPageTest.kt`, `SitemapRoutesTest.kt`

**Interfaces:**
- Produces: `fun Route.linuxInstallPage()` serving `GET /download/linux`; constants in `LinuxInstallPage.kt`: `const val LINUX_REPO_BASE_URL = "https://packages.hammer.ink"`, `const val LINUX_KEY_URL = "https://hammer.ink/assets/linux/hammer-archive-keyring.gpg"`, `const val LINUX_REPO_FILE_URL = "https://hammer.ink/assets/linux/hammer.repo"`. Absolute hammer.ink URLs on purpose: self-hosted servers point at our repo too.

- [ ] **Step 1: Write the failing test `LinuxInstallPageTest`**, set up like `HomePageTest`.
  - `` `GET renders apt commands with the repo added before install` ``: status 200; body contains `LINUX_KEY_URL`, contains `"deb [signed-by=/etc/apt/keyrings/hammer.gpg] $LINUX_REPO_BASE_URL/deb/any-distro any-version main"`, and `body.indexOf("/etc/apt/sources.list.d/hammer.list") < body.indexOf("apt install hammer")`.
  - `` `GET renders dnf and zypper commands with the repo added before install` ``: `body.indexOf("/etc/yum.repos.d/hammer.repo") < body.indexOf("dnf install hammer")`, and the `zypper addrepo $LINUX_REPO_FILE_URL` index is below `zypper install hammer`.
  - `` `GET credits Cloudsmith and links the direct downloads` ``: contains `cloudsmith.com`, `releases/latest/download/hammer.deb` and `releases/latest/download/hammer.rpm`.
  - `SitemapRoutesTest`: the expected lists gain `"<base>/download/linux"` right after the home page.

- [ ] **Step 2: Run and see it fail**
  Run: `./gradlew :server:test --tests '*LinuxInstallPageTest*' --tests '*SitemapRoutesTest*'`
  Expected: FAIL (404 / missing entry).

- [ ] **Step 3: Implement.** Model the route on `aboutPage` (`call.withDefaults()`, `page_stylesheet`, `title` from `msg("linux_install_title")`). The template follows the spec §7 content and order. Commands go in `<pre><code>` blocks with a copy button; `linux-install.js` copies the block's text to the clipboard. Use `docs/WEB-DESIGN-SYSTEM.md` tokens. New `Messages_en.properties` keys, prefixed `linux_install_`: title, lede, `debian_header`, `fedora_header`, `opensuse_header`, `step_add_repo`, `step_install`, `dnf_key_note`, `direct_header`, `direct_body`, `copy`, `copied`, `cloudsmith_credit` (text: "Package repository hosting is graciously provided by Cloudsmith."). On the home page's Linux card, add a link under the selector: `<a href="/download/linux">{{msg.home_download_linux_repo}}</a>`, with key text `Install from our apt/dnf repository`.

- [ ] **Step 4: Run tests and the parity check**
  Run: `./gradlew :server:test --tests '*LinuxInstallPageTest*' --tests '*SitemapRoutesTest*' --tests '*MessageBundleParityTest*' --tests '*HomePageTest*'`
  Expected: PASS.

- [ ] **Step 5: Commit**
  `git commit -m "Add the Linux repository install page"`

---

### Task 5: Repo client files and Linux docs (needs prerequisites)

Needs owner prerequisites 2–4: the Cloudsmith account and its answer on own-key support, the project GPG public key, and the DNS redirect. **If they're missing, stop and ask.**

**Files:**
- Create: `server/src/main/resources/assets/linux/hammer-archive-keyring.gpg` (binary, dearmored, `gpg --dearmor`). It holds our public key, plus Cloudsmith's metadata key if Cloudsmith cannot use ours.
- Create: `server/src/main/resources/assets/linux/hammer.repo`
- Create: `docs/LINUX-REPOS.md`
- Modify: `docs/HOW-TO-RELEASE.md`, `README.md` (Linux row), `SUPPORT.md` (log table)
- Test: `server/src/test/kotlin/com/darkrockstudios/apps/hammer/frontend/LinuxInstallPageTest.kt`

**Interfaces:**
- Consumes: URL constants from Task 4.

- [ ] **Step 1: Failing test** `` `the key and repo file are served` ``: `GET /assets/linux/hammer.repo` is 200, the body contains `baseurl=https://packages.hammer.ink/rpm/any-distro/any-version/$basearch`, `gpgcheck=1`, `repo_gpgcheck=1`, and `gpgkey=https://hammer.ink/assets/linux/hammer-archive-keyring.asc`. `GET /assets/linux/hammer-archive-keyring.gpg` is 200 and non-empty. (dnf needs an armored copy, so also add `hammer-archive-keyring.asc` and assert it is 200.) Register `staticResources` in the test app the same way `Frontend.kt` does.

- [ ] **Step 2: Run and see it fail.** Run: `./gradlew :server:test --tests '*LinuxInstallPageTest*'`. Expected: 404.

- [ ] **Step 3: Add the files.** `hammer.repo` has `[hammer]`, `name=Hammer`, the asserted lines, and `enabled=1`. If Cloudsmith signs metadata with its own key, `gpgkey=` lists both armored URLs, space-separated. `docs/LINUX-REPOS.md` covers:
  - the Cloudflare redirect rule (`packages.hammer.ink/*` → `https://dl.cloudsmith.io/public/<owner>/<repo>/$1`, 302);
  - which keys sign what;
  - Cloudsmith retention (keep 5 versions);
  - the emergency rebuild (`rebuild_from_tag`);
  - how to move hosts.

  `HOW-TO-RELEASE.md` gets the `linux-repo` token, a pointer to LINUX-REPOS.md, and a "Before tagging 4.0" checklist: the six secrets and two variables set, a dry-run publish green, the DMG opening without a Gatekeeper prompt on a clean Mac, and release notes saying DMG/PKG users must download 4.0 once by hand. README gets a link to `https://hammer.ink/download/linux` and the Cloudsmith credit. SUPPORT.md lists repo installs with the `.deb`/`.rpm` log path.

- [ ] **Step 4: Run tests, then verify against the real repo**
  Run: `./gradlew :server:test --tests '*LinuxInstallPageTest*'`. Expected: PASS.

  Then follow the page's commands in a `debian:stable` container and a `fedora:latest` container, once a release has been pushed to Cloudsmith. Expected: `apt install hammer` and `dnf install hammer` succeed with signature checks on.

- [ ] **Step 5: Commit**
  `git commit -m "Serve the Linux repo key and config, document the repos"`

---

### Task 6: macOS signing configuration (Gradle)

**Files:**
- Create: `buildSrc/src/main/java/macSigning.kt`
- Create: `desktop/developer-id-entitlements.plist`
- Modify: `desktop/build.gradle.kts` (the `macOS { }` block, `signMacAppResources`, and the `packageReleasePkg` dependency hook)
- Test: `buildSrc/src/test/java/MacSigningTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class MacSigning(
  	val composeIdentity: String,      // passed to Compose signing.identity
  	val codesignIdentity: String,     // full identity for signMacAppResources
  	val entitlementsFile: String,     // relative to desktop/
  	val runtimeEntitlementsFile: String,
  )
  fun macSigningFor(channel: DistributionChannel, developerIdEnabled: Boolean): MacSigning?
  fun Project.macDeveloperIdEnabled(): Boolean  // -PmacDeveloperId=true
  ```

- [ ] **Step 1: Write `MacSigningTest`**
  - `` `the App Store keeps its identity and sandbox entitlements` ``: `macSigningFor(MAC_APP_STORE, false)` and `(MAC_APP_STORE, true)` both equal `MacSigning("Adam Brown", "3rd Party Mac Developer Application: Adam Brown (8P3G3HT4J5)", "entitlements.plist", "runtime-entitlements.plist")`.
  - `` `GitHub builds sign with Developer ID only when enabled` ``: `macSigningFor(GITHUB, true) == MacSigning("Adam Brown", "Developer ID Application: Adam Brown (8P3G3HT4J5)", "developer-id-entitlements.plist", "developer-id-entitlements.plist")`, and `macSigningFor(GITHUB, false) == null`.
  - `` `no other channel signs` ``: every other channel with `true` gives `null`.

- [ ] **Step 2: Run and see it fail.** Run: `./gradlew -p buildSrc test --tests '*MacSigningTest*'`. Expected: unresolved reference.

- [ ] **Step 3: Implement `macSigning.kt`, then wire it in.**
  - `developer-id-entitlements.plist`: `com.apple.security.cs.allow-jit`, `com.apple.security.cs.allow-unsigned-executable-memory`, `com.apple.security.cs.disable-library-validation`, `com.apple.security.cs.allow-dyld-environment-variables`, all `true`, and **no** `app-sandbox` key.
  - In `build.gradle.kts`: `val macSigning = macSigningFor(project.distributionChannel(), project.macDeveloperIdEnabled())`. Replace the `if (isAppStoreRelease) { signing … entitlements … }` block with `macSigning?.let { signing { sign.set(true); identity.set(it.composeIdentity) }; entitlementsFile.set(file(it.entitlementsFile)); runtimeEntitlementsFile.set(file(it.runtimeEntitlementsFile)) }`. Keep the provisioning profiles under `isAppStoreRelease`.
  - `signMacAppResources`: `onlyIf { macSigning != null && macOS }`, using `macSigning.codesignIdentity` and its entitlements files instead of the hard-coded ones.
  - Extend the existing `packageReleasePkg` hook to `name == "packageReleasePkg" || name == "packageReleaseDmg"`.

- [ ] **Step 4: Verify**
  Run: `./gradlew -p buildSrc test && ./gradlew :desktop:help -Pchannel=mac-app-store && ./gradlew :desktop:help -Pchannel=github -PmacDeveloperId=true`
  Expected: tests pass and both configurations configure without errors. The App Store identity and entitlements are pinned by the first test.

- [ ] **Step 5: Commit**
  `git commit -m "Choose macOS signing identity per channel, add Developer ID signing"`

---

### Task 7: Sign, notarize and zip the macOS release

**Files:**
- Create: `.github/scripts/mac-keychain.sh` (taken from `publish-mac-app-store.yml`'s "Set up signing keychain")
- Create: `desktop/scripts/notarize.sh`
- Modify: `.github/workflows/publish-mac-app-store.yml` (call the shared script, same behaviour)
- Modify: `.github/workflows/prepare-release.yml` (`macos` job)

**Interfaces:**
- Consumes: `-PmacDeveloperId=true` (Task 6).
- Produces:
  - `mac-keychain.sh <app-p12-env-name> <installer-p12-env-name> <password-env-name>`: creates and unlocks a temporary keychain, imports the WWDR G3 intermediate and both `.p12`s, and writes `KEYCHAIN_PATH`/`KEYCHAIN_PASSWORD` to `$GITHUB_ENV`.
  - `notarize.sh <file>`: reads `NOTARY_KEY_PATH`, `NOTARY_KEY_ID`, `NOTARY_ISSUER_ID`; runs `xcrun notarytool submit "$1" --wait`, failing unless the status is `Accepted` (on failure it prints `notarytool log`).
  - `hammer.zip` attached to the release.

- [ ] **Step 1: Extract `mac-keychain.sh`** and switch `publish-mac-app-store.yml` to it, passing `MAC_DISTRIBUTION_CERT_P12_BASE64 MAC_INSTALLER_CERT_P12_BASE64 MAC_DISTRIBUTION_CERT_PASSWORD`. Run `actionlint`. Expected: clean, and the diff to that workflow is only the step body.

- [ ] **Step 2: Write `notarize.sh`.** Verify with `bash -n`.

- [ ] **Step 3: Rewrite the `macos` job.** Job env `HAS_MAC_DEVELOPER_ID_APP_P12_BASE64`. When true:
  1. Keychain via `mac-keychain.sh MAC_DEVELOPER_ID_APP_P12_BASE64 MAC_DEVELOPER_ID_INSTALLER_P12_BASE64 MAC_DEVELOPER_ID_CERT_PASSWORD`.
  2. Write `MAC_APP_STORE_CONNECT_API_KEY_P8` to `$RUNNER_TEMP/AuthKey.p8` and export `NOTARY_*`.
  3. Package the DMG with `-PmacDeveloperId=true` and rename it.
  4. `notarize.sh hammer.dmg`, then `xcrun stapler staple hammer.dmg`.
  5. **Before packaging the PKG**, zip `desktop/build/installers/main-release/app/*.app` with `ditto -c -k --keepParent` into `notarize.zip`, run `notarize.sh notarize.zip`, `xcrun stapler staple` the `.app`, and `ditto` it again into `desktop/build/installers/main-release/zip/hammer.zip`. This is the DMG-format app (Review Focus 5).
  6. Package the PKG with `-PmacDeveloperId=true`, rename it, notarize and staple it.
  7. Run `writeAssetDigests` over the DMG, the PKG **and** `zip/hammer.zip`, and attach `hammer.zip` with the other assets.

  When false: today's steps plus the warning. The keychain clean-up step (`if: always()`) deletes the keychain and the `.p8`.

- [ ] **Step 4: Gatekeeper checks in the same job**, after stapling: `xcrun stapler validate` on the DMG, the PKG and the extracted app; `spctl -a -vv -t install hammer.pkg`; `spctl -a -vv -t open --context context:primary-signature hammer.dmg`; and `spctl -a -vv` on the stapled `.app`. Each must report `accepted` / `source=Notarized Developer ID`.

- [ ] **Step 5: Verify** with `actionlint`. After the secrets exist, push a test tag on a fork or `workflow_dispatch` copy. Expected: Step 4's checks pass, and the downloaded DMG opens on a clean Mac with no Gatekeeper prompt.

- [ ] **Step 6: Commit**
  `git commit -m "Sign and notarize the GitHub DMG and PKG, attach a notarized hammer.zip"`

---

### Task 8: PKG update probe (decides Task 9's PKG mapping)

This needs a Mac with an admin account, either the owner's or a `macos-latest` runner via a throwaway `workflow_dispatch` job that is not merged. If neither is available, record **"not possible"** (owner's default: PKG stays notify-only).

- [ ] **Step 1: Install the signed PKG**: `sudo installer -pkg hammer.pkg -target /`.
- [ ] **Step 2: As the admin user, without sudo, run the swap that Nucleus's `MacZipUpdateScript` performs.** Unzip `hammer.zip` with `ditto -x -k` into `/Applications/.nucleus-update-test`. Then `mv /Applications/hammer.app /Applications/hammer.app.nucleus-old-test`, then `mv` the staged app into place, then `rm -rf /Applications/hammer.app.nucleus-old-test /Applications/.nucleus-update-test`.
  Expected for **"possible"**: every command exits 0 and the new app launches. Any non-zero exit means **"not possible"**.
- [ ] **Step 3: Record the result** in Task 9's PR description and in `UpdaterSupport.kt`'s KDoc.

---

### Task 9: Updater and metadata switch to `hammer.zip`

Merge this **only after** the Developer ID secrets are configured (Global Constraints).

**Files:**
- Modify: `desktop/src/jvmMain/kotlin/com/darkrockstudios/apps/hammer/desktop/update/UpdaterSupport.kt`
- Modify: `buildSrc/src/main/java/updateMetadata.kt`
- Modify: `.github/workflows/prepare-release.yml` (`set-release-body` "Write update metadata")
- Test: `UpdaterSupportTest.kt`, `buildSrc/src/test/java/UpdateMetadataTest.kt`

**Interfaces:**
- Produces: `fun updateMetadataFiles(digestsDir: File, version: String, releaseDate: String, requireMacZip: Boolean = false): Map<String, String>`, with the Gradle property `-PrequireMacZip=true`.

- [ ] **Step 1: Failing tests**
  - `UpdaterSupportTest.every format maps to the asset it updates from`: `assertEquals("zip", nucleusExecutableType(PackageFormat.DMG))`. PKG expects `"zip"` if Task 8 said possible, `"dmg"` otherwise.
  - Rename `` `macOS installs are announced but not installed in place` `` to `` `the DMG installs in place` ``: `assertTrue(canInstallInPlace(PackageFormat.DMG))`. PKG gets `assertTrue` or `assertFalse` per Task 8.
  - `UpdateMetadataTest`: new test `` `a missing mac zip fails when it is required` `` that writes the dmg and pkg sidecars but no zip, calls `updateMetadataFiles(dir, "1", "d", requireMacZip = true)`, and asserts `IllegalStateException` with a message containing `hammer.zip`. The existing `` `the mac zip is listed first when present and skipped when absent` `` stays green with the default `false`.

- [ ] **Step 2: Run and see them fail.** Run: `./gradlew :desktop:jvmTest --tests '*UpdaterSupportTest*' && ./gradlew -p buildSrc test --tests '*UpdateMetadataTest*'`. Expected: FAIL.

- [ ] **Step 3: Implement.**
  - `nucleusExecutableType(DMG) = "zip"`, and `canInstallInPlace(DMG) = true`. Set PKG per Task 8.
  - Rewrite the KDoc. Drop "the release has no such zip yet". For PKG, say why it is or isn't in place.
  - `updateMetadataFiles` treats `hammer.zip` as required when `requireMacZip`. `writeUpdateMetadata` reads `findProperty("requireMacZip") == "true"`.
  - In `set-release-body`, expose `HAS_MAC_DEVELOPER_ID_APP_P12_BASE64` and pass `-PrequireMacZip=${{ env.HAS_MAC_DEVELOPER_ID_APP_P12_BASE64 }}`.

- [ ] **Step 4: Run tests and see them pass.** Run: the same commands as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**
  `git commit -m "Update DMG installs in place from the notarized hammer.zip"`
