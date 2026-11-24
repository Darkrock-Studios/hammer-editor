# Signed Mac installers and Linux package repositories (4.0)

Status: draft for approval · 2026-11-24

## Intent

Hammer's GitHub-release desktop installers ship unsigned today. On macOS users hit Gatekeeper's
"cannot be opened", the DMG and PKG cannot update themselves, and Linux users have no way to get
updates through their system package manager.

For 4.0 we want:

1. **macOS:** the DMG and PKG signed with Developer ID and notarized, so they open cleanly, and
   so the DMG can finally **update itself in-app** (today Mac installs are only told about
   releases, see `UpdaterSupport.kt`: "only a signed, notarized zip could swap" the bundle).
   The PKG gets in-app updates too if, and only if, the updater can replace what it installed
   (see §2).
2. **Linux:** an **apt** and a **dnf** repository, so users add Hammer once and it updates with
   the rest of their system. Packages served by the repos are a **new distribution channel**
   with in-app updates **off**; the package manager is their only update path.
3. A **Linux install page** on hammer.ink, linked from the home page's download section, giving
   the commands to add the repository and then install.

## Goals

- The GitHub-release DMG, PKG and RPM are signed (the DMG and PKG also notarized).
- DMG installs update in-app the way MSI/EXE/DEB/RPM/AppImage already do; PKG installs too
  where technically possible.
- apt and dnf repos on Cloudsmith, reachable at an address on **our** domain, updated only when
  a release is **published** (never from a draft).
- The repo builds report their own channel; the existing `DistributionChannel`/`PackageFormat`
  infrastructure decides update behaviour, with no new special-casing.
- Moving off Cloudsmith later must not require users to change their repo configuration.
- No manual steps in a normal release, and no meaningful new running cost.

## Non-goals

- **Windows signing (MSI/EXE). Shelved for 4.0.** We looked at Azure Artifact Signing
  (individuals only in the US/Canada), SignPath Foundation (free, but every release signing
  request must be approved by hand, which breaks unattended releases) and paid OV
  certificates (a few hundred dollars a year). None fits "no manual steps, low cost" today.
- Signing the standalone GitHub `.deb`. dpkg ignores embedded `.deb` signatures; the trust
  comes from the signed apt repo metadata.
- A Chrome/VS Code-style package that adds the repo on install. Users add the repo from the
  install page; the GitHub `.deb`/`.rpm` stay as they are and keep updating themselves.
- Guaranteeing that a GitHub-`.deb`/`.rpm` user who later adds the repo moves onto the repo
  build. Repo and GitHub packages carry the **same** version (owner's decision): if such a user
  takes the in-app update first, apt/dnf sees the version already installed and they stay on
  the GitHub build with in-app updates. Accepted as harmless.
- Intel macOS builds, AppImage/Flatpak/Snap changes, Microsoft Store (MSIX) changes.

## Anti-goals (would make this a failure even if it technically works)

- A draft or unpublished release reaching apt/dnf users.
- A repo-installed Hammer updating itself in-app, fighting the package manager.
- The signing private keys ending up anywhere but GitHub secrets (and, unavoidably, Cloudsmith
  for what it signs).
- Update metadata (`latest*.yml`) whose sha512 does not match the signed file actually attached
  (digests must be taken **after** signing/notarizing/stapling).
- Breaking the Mac App Store build, whose signing path shares `desktop/build.gradle.kts`.
- Blocking 3.x releases while the 4.0 prerequisites are still being set up (see §0).

## Decisions made with the owner

| Area | Decision |
|---|---|
| macOS | Developer ID Application + Developer ID Installer certs, notarized; DMG gets in-app updates via a signed, notarized `hammer.zip` |
| PKG updates | In-app if the updater can replace the installed app; otherwise the PKG keeps "notify only" |
| Windows | Shelved (no manual steps, no high cost) |
| Linux hosting | **Cloudsmith** open-source tier (≥50 GB storage, 200 GB delivery, attribution required) |
| Linux domain | Our own domain in front of Cloudsmith, so the host can be swapped later |
| Repo onboarding | An install page with "add repo" commands, then the install command |
| Repo channel | New channel; in-app updates off |
| Repo package version | Same as the GitHub packages (no revision bump, no epoch) |
| Artifact hand-off to repos | **Approach B** (below), with a **manual emergency rebuild** fallback |

## Approaches considered for getting repo packages to Cloudsmith

- **A. Attach to the draft release; publish copies them to Cloudsmith.** Mirrors `hammer.snap`
  exactly. Cost: two extra, confusingly similar assets on every release page.
- **B. Keep them as workflow artifacts of the release build; publish fetches them.** Clean
  release page, and what reaches the repo is exactly what was built and reviewed in the draft.
  Cost: the publish job has to find the right earlier run, and artifacts expire. **Chosen.**
- **C. Rebuild at publish time from the tag.** No hand-off, but the repo gets a build nobody
  reviewed, and every release pays for a second Linux build. Rejected as the normal path;
  **kept as a manual, opt-in emergency path** for when B's artifact is gone (§5).

## Design

### 0. Rolling out while prerequisites are missing (builder's call, confirmed with owner)

The work lands on `develop` piece by piece while 3.x releases continue. Each new signing or
repo step checks for its secret:

- **Secret not set:** the step is skipped with a visible `::warning::` in the run summary, and
  everything behaves as today (e.g. `hammer.zip` stays optional in `updateMetadata.kt`, the
  `linux-repo` publish is a no-op).
- **Secret set but the step fails:** the release fails. Never silently ship an unsigned build
  once signing is configured.

`hammer.zip` becomes **required** in `updateMetadata.kt` only when the macOS signing secrets
are present (or in the final PR, once they are). 4.0 is not tagged until every prerequisite
below is in place; add a short pre-4.0 checklist to `docs/HOW-TO-RELEASE.md`.

### 1. Distribution channel and platform token

- Add `LINUX_REPO("linux-repo", "Linux package repository")` to **both** `DistributionChannel`
  enums (`:base` and `buildSrc/src/main/java/distributionChannel.kt`) and extend
  `DistributionChannelTest`. `channelMayUpdate` already returns false for anything but
  `GITHUB`/`APPIMAGE`, so in-app updates are off with no updater change. The builder must walk
  every `when` on `DistributionChannel` (it is deliberately greppable) and give the new channel
  the GitHub-like answer for "get the app" links, etc.
- One channel for both repos (not separate apt/dnf channels): `PackageFormat` (`deb`/`rpm`)
  already distinguishes them.
- Add `LINUX_REPO("linux-repo", "Linux repos")` to `Platform` in `buildSrc/.../platforms.kt`
  (a client store, so it is offered in "Targeted" releases), and to the token catalog used by
  `.github/scripts/discord-release-message.sh`.
- Repo packages keep the package name `hammer` and the same version as the GitHub packages.

### 2. macOS: Developer ID signing, notarization, in-app updates

In the `macos` job of `prepare-release.yml`:

- New secrets: Developer ID Application `.p12`, Developer ID Installer `.p12`, their password.
  Keychain set-up mirrors `publish-mac-app-store.yml` (WWDR intermediate, two single-identity
  `.p12` imports, partition list). The builder may extract that block into a shared script used
  by both workflows.
- Signing for the `github` channel uses Compose's built-in `macOS { signing { } }` with the
  Developer ID Application identity, hardened runtime, and **non-sandbox** entitlements
  (the App Store `entitlements.plist` must **not** be used: it enables the sandbox and would
  trigger `SandboxStartup`). The JVM needs the usual hardened-runtime exceptions (JIT,
  unsigned executable memory, library validation); Compose's defaults cover this, builder to
  confirm.
- `signMacAppResources` is generalised: today it is gated to the App Store channel with a
  hard-coded "3rd Party Mac Developer Application" identity. It must run for the `github`
  channel too, with the Developer ID identity and the non-sandbox entitlements, because the
  dylibs under `Contents/app/resources/` are otherwise ad-hoc signed and fail notarization.
  App Store behaviour must stay byte-for-byte the same.
- PKG is signed with the Developer ID Installer identity.
- Notarization: `xcrun notarytool submit --wait` with the **existing** App Store Connect API key
  (already a secret for the Mac App Store publish), then `xcrun stapler staple`. Done for the
  DMG, the PKG, and the app that goes into `hammer.zip`.
- `hammer.zip`: the signed, notarized, stapled `Hammer.app`, zipped with
  `ditto -c -k --keepParent`. Attached to the release. `latest-mac.yml` already lists
  `hammer.zip` first and treats it as optional (`updateMetadata.kt`); it becomes required per §0.
- Digests (`writeAssetDigests`) are computed after stapling.
- Updater (`UpdaterSupport.kt`): DMG resolves to the Nucleus executable type that downloads
  `hammer.zip`, and `canInstallInPlace` returns true for it. Update the KDoc and
  `UpdaterSupportTest`.
- **PKG (owner's decision):** the PKG installs `/Applications/Hammer.app` as root. Verify early
  whether Nucleus can replace that bundle from a user process (including via an admin prompt).
  If it can, the PKG behaves like the DMG. If it cannot, the PKG keeps today's behaviour:
  told about releases and sent to the release page, `canInstallInPlace` false, with the
  KDoc saying why. No other workaround (no `.pkg` hand-off to Installer).

Users on today's unsigned DMG/PKG builds cannot self-update into the signed build; they need
one manual download. Call this out in the 4.0 release notes.

### 3. Linux: GPG key and RPM signing

- One new project GPG key, RSA 4096 (accepted by `rpm` on RHEL 9 and current Fedora), private
  key and passphrase in GitHub secrets. The public key is published on our domain.
- `rpmsign --addsign` both the GitHub-release `hammer.rpm` and the repo `.rpm`, before digests.
- `.deb` files are not signed (see non-goals).

### 4. (Removed: Windows signing is shelved, see non-goals.)

### 5. Linux: repo builds (approach B) and publishing

**Build (prepare-release.yml).** New `linux-repo` job, parallel to `linux`: builds
`:desktop:packageReleaseDeb -Pchannel=linux-repo -Pformat=deb` and the RPM equivalent, one
Gradle invocation per format (DEVELOPMENT.md: two packaging tasks in one invocation share the
first format), signs the RPM, and uploads both as the workflow artifact `linux-repo-packages`
with the maximum retention the repo allows (90 days on a public repo). It does **not** attach
anything to the release. It joins `set-release-body`'s `needs`, so a failed repo build blocks
the release like any other platform.

**Publish.** New reusable `publish-linux-repo.yml` (`workflow_call` + `workflow_dispatch`),
called from `publish-release.yml`, gated on no `+` suffix or `+linux-repo` like the store jobs,
and added to `notify`'s `needs`.

- Locate the `prepare-release.yml` run for the tag (tag-push runs carry the tag as their head
  branch). **Do not filter on the run's overall status:** with auto-publish, the
  `Publish release` step inside that same run fires `publish-release.yml` before the run has
  finished. Instead require that the run's **`linux-repo` job** concluded successfully
  (it always has by then, because `set-release-body` needs it).
- Download `linux-repo-packages` from that run, check the package versions match the tag's
  version, and upload to Cloudsmith (`deb` → apt repo, `rpm` → rpm repo, distribution
  `any-distro/any-version`).
- **Normal-path failure** (no such run, the job did not succeed, or the artifact has expired):
  fail with a message pointing at the emergency path below. Do not suggest re-running the old
  release run: GitHub only allows re-runs within 30 days, and re-running would also re-run
  `set-release-body` (re-uploading `latest*.yml` and, with auto-publish, re-marking that
  release as latest).

**Manual emergency rebuild (owner's decision).** `publish-linux-repo.yml`'s
`workflow_dispatch` takes `release_tag` plus a boolean `rebuild_from_tag` (default false). When
true, it checks out the tag, builds the repo `.deb` and `.rpm` exactly as the `linux-repo` job
does, signs the RPM, and uploads them. It is never used automatically.

- New secret: Cloudsmith API key.
- Cloudsmith retention: keep the last few versions (e.g. 5) to stay well inside 50 GB.

### 6. Cloudsmith and our domain

- Cloudsmith custom domains are Ultra/Enterprise only, so on the free tier we put a
  **redirect** on our domain, proposed `packages.hammer.ink` (Cloudflare redirect rule →
  `dl.cloudsmith.io/public/<owner>/<repo>/...`). apt and dnf follow redirects; trust comes from
  the GPG signatures, not the hostname. Moving host later = changing the redirect.
- The public key(s), `hammer.repo` (dnf/zypper), and the apt sources line users paste all use
  our domain, never `cloudsmith.io` directly.
- **Signing-key lock-in:** if Cloudsmith only signs repo metadata with a key it generates, a
  later move off Cloudsmith means users must install a new key. Before building, the owner
  asks Cloudsmith support whether an open-source repo can use our own GPG key. Either way the
  design works: with our key, `hammer.repo`'s `gpgkey=` lists one key; with theirs, it lists
  both (our key for package signatures, theirs for repo metadata), and the apt keyring file we
  serve contains whichever signs `Release`.
- Attribution to Cloudsmith goes on the install page and in the README.

### 7. Linux install page (hammer.ink)

- New server page, e.g. `/download/linux`, alongside `AboutPage.kt` / `about.mustache`,
  strings in `Messages*.properties` (translated through Crowdin like the rest of the site);
  commands are not translated.
- The Linux card in `home.mustache` links to it. The existing direct `.deb`/`.rpm`/AppImage/
  Flatpak/Snap options stay.
- Content, per distro, with copy buttons on each command block. **Add-repo commands always
  come before the install command:**

```
Debian / Ubuntu
  1. Add the signing key and repository
     sudo install -d -m 0755 /etc/apt/keyrings
     curl -fsSL https://packages.hammer.ink/hammer.gpg | sudo tee /etc/apt/keyrings/hammer.gpg > /dev/null
     echo "deb [signed-by=/etc/apt/keyrings/hammer.gpg] https://packages.hammer.ink/deb any-version main" \
       | sudo tee /etc/apt/sources.list.d/hammer.list
  2. Install
     sudo apt update && sudo apt install hammer

Fedora / RHEL
  1. Add the repository
     sudo curl -fsSL -o /etc/yum.repos.d/hammer.repo https://packages.hammer.ink/hammer.repo
  2. Install (dnf asks you to confirm the signing key the first time)
     sudo dnf install hammer

openSUSE
  sudo zypper addrepo https://packages.hammer.ink/hammer.repo && sudo zypper install hammer

Rather not add a repository? Download hammer.deb / hammer.rpm (these update themselves).
```

  Exact paths follow Cloudsmith's layout (builder). `curl` for the `.repo` file rather than
  `dnf config-manager`, whose syntax differs between dnf 4 (RHEL) and dnf 5 (Fedora).
- Where the key and `.repo` files are served from (the redirect domain with an exception rule,
  or this site's static assets) is the builder's call, as long as it is our domain.
- Self-hosted Hammer servers serve this page too and will point at our repo, same as the
  existing download links.

### 8. Docs

- `docs/HOW-TO-RELEASE.md`: the `linux-repo` token, the emergency rebuild, the pre-4.0
  checklist (§0).
- `DEVELOPMENT.md`: the new channel and token.
- `SUPPORT.md`: log location for repo installs (same as `.deb`/`.rpm`).
- README: Linux repo / install page link, Cloudsmith attribution.

## Prerequisites the owner provides (not code)

1. Apple: Developer ID Application and Developer ID Installer certificates (`.p12`).
2. Cloudsmith open-source account + two public repos (deb, rpm); API key; answer on own-key
   support.
3. A GPG key generated for the project (or the builder generates it and hands it over).
4. DNS/Cloudflare control of the chosen subdomain.

## Left to the builder

- Exact Nucleus executable type / asset for macOS zip updates, and the PKG feasibility check.
- Cloudsmith repo layout and retention numbers.
- Whether keychain set-up becomes a shared script.
- How the publish job queries the `linux-repo` job's conclusion (e.g. `gh api` on the run's
  jobs).
- Page styling (follow `docs/WEB-DESIGN-SYSTEM.md`) and the exact route.
- Order of work. Suggested: channel/platform plumbing → Linux repo build + publish → install
  page → macOS.
