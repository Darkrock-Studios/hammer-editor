# Signed desktop installers and Linux package repositories (4.0)

Status: draft for approval · 2026-11-24

## Intent

Hammer's GitHub-release desktop installers (DMG, PKG, MSI, EXE, DEB, RPM) ship unsigned today.
Users hit Gatekeeper's "cannot be opened" on macOS and SmartScreen's "Unknown publisher" on
Windows, and Linux users have no way to get updates through their system package manager.

For 4.0 we want:

1. **macOS:** the DMG and PKG signed with Developer ID and notarized, so they open cleanly, and
   so they can finally **update themselves in-app** (today they are only told about releases,
   see `UpdaterSupport.kt`: "only a signed, notarized zip could swap" the bundle).
2. **Windows:** the MSI and EXE signed, so SmartScreen names a publisher and reputation can
   build across releases.
3. **Linux:** an **apt** and a **dnf** repository, so users add Hammer once and it updates with
   the rest of their system. Packages served by the repos are a **new distribution channel**
   with in-app updates **off**; the package manager is their only update path.
4. A **Linux install page** on hammer.ink, linked from the home page's download section, giving
   the commands to add the repository and then install.

## Goals

- Every desktop installer attached to a GitHub release is signed, except the `.deb` (see
  non-goals) and the AppImage (unchanged).
- DMG and PKG installs update in-app the way MSI/EXE/DEB/RPM/AppImage already do.
- apt and dnf repos on Cloudsmith, reachable at an address on **our** domain, updated only when
  a release is **published** (never from a draft).
- The repo builds report their own channel; the existing `DistributionChannel`/`PackageFormat`
  infrastructure decides update behaviour, with no new special-casing.
- Moving off Cloudsmith later must not require users to change their repo configuration.

## Non-goals

- Signing the standalone GitHub `.deb`. dpkg ignores embedded `.deb` signatures; the trust
  comes from the signed apt repo metadata.
- A Chrome/VS Code-style package that adds the repo on install. Users add the repo from the
  install page; the GitHub `.deb`/`.rpm` stay as they are and keep updating themselves.
- Intel macOS builds, AppImage/Flatpak/Snap changes, Microsoft Store (MSIX) changes.
- Instant SmartScreen trust. No certificate gives it any more; reputation builds over releases.

## Anti-goals (would make this a failure even if it technically works)

- A draft or unpublished release reaching apt/dnf users.
- A repo-installed Hammer updating itself in-app, fighting the package manager.
- The signing private keys ending up anywhere but GitHub secrets (and, unavoidably, SignPath /
  Cloudsmith for what those services sign).
- Update metadata (`latest*.yml`) whose sha512 does not match the signed file actually attached
  (digests must be taken **after** signing/notarizing/stapling).
- Breaking the Mac App Store build, whose signing path shares `desktop/build.gradle.kts`.

## Decisions made with the owner

| Area | Decision |
|---|---|
| macOS | Developer ID Application + Developer ID Installer certs, notarized; DMG **and** PKG get in-app updates via a signed, notarized `hammer.zip` |
| Windows | **SignPath Foundation** (free OSS signing). Publisher will read "SignPath Foundation" |
| Linux hosting | **Cloudsmith** open-source tier (≥50 GB storage, 200 GB delivery, attribution required) |
| Linux domain | Our own domain in front of Cloudsmith, so the host can be swapped later |
| Repo onboarding | An install page with "add repo" commands, then the install command |
| Repo channel | New channel; in-app updates off |
| Artifact hand-off to repos | **Approach B** (below): repo packages are workflow artifacts, not release assets |

## Approaches considered for getting repo packages to Cloudsmith

- **A. Attach to the draft release; publish copies them to Cloudsmith.** Mirrors `hammer.snap`
  exactly. Cost: two extra, confusingly similar assets on every release page.
- **B. Keep them as workflow artifacts of the release build; publish fetches them.** Clean
  release page, and what reaches the repo is exactly what was built and reviewed in the draft.
  Cost: the publish job has to find the right earlier run, and artifacts expire. **Chosen.**
- **C. Rebuild at publish time from the tag.** No hand-off, but the repo gets a build nobody
  reviewed, and every release pays for a second Linux build. Rejected.

## Design

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
- Repo packages keep the package name `hammer`, so a GitHub-`.deb` user who adds the repo is
  moved onto the repo build (and off in-app updates) at the next release.

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
  `hammer.zip` first and treats it as optional (`updateMetadata.kt`); make it **required**
  so a release that lacks it fails instead of silently leaving Macs without updates.
- Digests (`writeAssetDigests`) are computed after stapling.
- Updater (`UpdaterSupport.kt`): DMG and PKG resolve to the Nucleus executable type that
  downloads `hammer.zip`, and `canInstallInPlace` returns true for both. Update the KDoc and
  `UpdaterSupportTest`.

**Risk the builder must verify early:** the PKG installs `/Applications/Hammer.app` as root.
If Nucleus cannot replace a root-owned bundle (or prompt for admin rights), PKG in-place
updates are not possible as designed; stop and raise it with the owner rather than ship PKG
as "notify only" silently.

Users on today's unsigned DMG/PKG builds cannot self-update into the signed build; they need
one manual download. Call this out in the 4.0 release notes.

### 3. Windows: SignPath

In the `windows` job of `prepare-release.yml`, after packaging and renaming and **before**
`writeAssetDigests` and the release upload:

- Upload the unsigned `hammer.msi` and `hammer.exe` with `actions/upload-artifact`, submit them
  with SignPath's GitHub Action (`signpath/github-action-submit-signing-request`,
  `wait-for-completion: true`), and replace the unsigned files with the signed output.
- The SignPath artifact configuration should deep-sign where SignPath supports it: the MSI,
  and the `hammer.exe` launcher inside it (helps with antivirus false positives). If SignPath
  cannot sign inside the jpackage EXE wrapper, sign the outer EXE only.
- New secrets: SignPath API token and organisation id; project and signing-policy slugs can
  live in the workflow.

SignPath Foundation conditions that the owner must satisfy (outside the code): apply and be
approved; MFA for every team member on GitHub and SignPath; no proprietary components (the
builder should list the desktop app's dependency licences for the application); publish a
code-signing policy page; credit "Free code signing provided by SignPath.io, certificate by
SignPath Foundation" (README and the Windows download area). The policy page goes in the repo
as `docs/CODE_SIGNING_POLICY.md`, linked from the README.

### 4. Linux: GPG key and RPM signing

- One new project GPG key (RSA 4096 or ed25519 — builder's call, but it must be accepted by
  `rpm` on RHEL 9 and current Fedora; RSA 4096 is the safe choice), private key and passphrase
  in GitHub secrets. The public key is published on our domain.
- `rpmsign --addsign` both the GitHub-release `hammer.rpm` and the repo `.rpm`, before digests.
- `.deb` files are not signed (see non-goals).

### 5. Linux: repo builds (approach B)

- New `linux-repo` job in `prepare-release.yml` (parallel to `linux`): builds
  `:desktop:packageReleaseDeb -Pchannel=linux-repo -Pformat=deb` and the RPM equivalent, one
  Gradle invocation per format (see DEVELOPMENT.md: two packaging tasks in one invocation share
  the first format), signs the RPM, and uploads both as the workflow artifact
  `linux-repo-packages` with the maximum retention the repo allows (90 days on a public repo).
  It does **not** attach anything to the release. It joins `set-release-body`'s `needs`, so a
  failed repo build blocks the release like any other platform.
- New reusable `publish-linux-repo.yml` (`workflow_call` + `workflow_dispatch`, input
  `release_tag`), called from `publish-release.yml` and gated on no `+` suffix or
  `+linux-repo`, like the store jobs; added to `notify`'s `needs`.
  - Finds the **successful** `prepare-release.yml` run for that tag (tag-push runs carry the tag
    as their head branch: `gh run list --workflow prepare-release.yml --branch "$TAG"
    --status success`), downloads `linux-repo-packages` from it.
  - Verifies the package versions match the tag's version.
  - Uploads to Cloudsmith with the Cloudsmith CLI/action (`deb` to the apt repo for
    distribution `any-distro/any-version`, `rpm` to the rpm repo `any-distro/any-version`).
  - If the artifact has expired or no successful run exists, fails with a message saying how
    to recover: re-run the `linux-repo` job of that release run, then re-run this publish via
    `workflow_dispatch`.
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

- `docs/HOW-TO-RELEASE.md`: the `linux-repo` token, what to do if the repo publish fails or the
  artifact expired.
- `DEVELOPMENT.md`: the new channel and token.
- `SUPPORT.md`: log location for repo installs (same as `.deb`/`.rpm`).
- README: Linux repo link, SignPath and Cloudsmith attribution, code-signing policy link.

## Prerequisites the owner provides (not code)

1. Apple: Developer ID Application and Developer ID Installer certificates (`.p12`).
2. SignPath Foundation application approved; API token; MFA on.
3. Cloudsmith open-source account + two public repos (deb, rpm); API key; answer on own-key
   support.
4. A GPG key generated for the project (or the builder generates it and hands it over).
5. DNS/Cloudflare control of the chosen subdomain.

## Left to the builder

- Exact Nucleus executable type / asset for macOS zip updates.
- SignPath artifact-configuration XML and deep-signing coverage.
- Key algorithm (within the constraint above), Cloudsmith repo layout and retention numbers.
- Whether keychain set-up becomes a shared script.
- Page styling (follow `docs/WEB-DESIGN-SYSTEM.md`) and the exact route.
- Order of work. Suggested: channel/platform plumbing → Linux repo build + publish → install
  page → macOS → Windows (SignPath approval may take a while, so the application should be
  sent first).
