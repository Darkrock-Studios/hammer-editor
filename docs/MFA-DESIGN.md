# Multi-factor authentication

Design and rollout plan for a second factor on Hammer accounts. The first pass is TOTP codes for
authenticator apps, opt-in per account. Passkeys (WebAuthn) are designed below but shelved; the
TOTP work leaves room for them. Nothing here needs a third-party service, so it works on any
self-hosted server.

## Where auth lives today

| Surface | Entry point | Result |
|---|---|---|
| API login | `POST /api/account/login` in `AccountRoutes.kt` → `AccountsComponent.login` → `AccountsRepository.login` | `Token` (bearer + refresh, per `(userId, installId)`) |
| Web login | `POST /login` in `LoginPage.kt`, same repository call with `installId = "web"` | `UserSession` cookie |
| Token refresh | `POST /api/account/refresh_token/{userId}` | new `Token`, no password |
| Pairing (Wear) | `POST /api/account/pair_install/{userId}`, bearer-authenticated | `Token` for another install |
| Password reset | `PasswordResetRepository`, email link | password hash replaced |
| Client | `AccountUseCase.setupServer` and `AccountReauthUseCase.reauthenticate` call `ServerAccountApi.login`; `AccountSettingsComponent` drives `ServerSetupDialog`; `ServerReauthenticationComponent` drives `ReauthenticationUi` | `ServerSettings` with tokens |
| CLI / MCP | `operations` module, `account.login` | same use case |

Useful existing pieces: Argon2 via BouncyCastle, `TokenHasher` (keyed HMAC-SHA256 from the
keyring's `tokenHmac` role), `ContentEncryptors` (AES-GCM at rest, keyed per account by
`cipher_secret`), `SecurityRepository.recordLoginAttempt` plus brute-force alerts,
`LOGIN_RATE_LIMIT`, `TokenMaintenanceJob` for purging, `ServerConfig.publicUrl`, HTMX partials on
the dashboard, and Kache for in-memory caches.

Only password logins need a second factor. Token refresh and `pair_install` already prove
possession of a session and stay as they are, so the Wear app is unaffected.

## Decisions

1. **TOTP only for the first pass, with the design left open to passkeys.** Passkeys are not
   scheduled, but the challenge carries a `methods` list, `MfaRepository.resetAll` and the
   admin reset cover every factor, and the Security section is laid out as a list of factors,
   so adding them later touches no existing wire format. The shelved design is at the end of
   this document.
2. **Enrollment happens in the web dashboard**, in a new Security section next to pen name and
   account deletion. The apps only answer the challenge at login. In-app enrollment can come
   later without changing the server.
3. **Recovery codes are mandatory with TOTP.** Eight single-use codes, shown once at enrollment.
4. **Admins can reset a user's MFA** from the admin users page, for lockouts.
5. **The login API is a challenge, not a combined form.** A correct password on an MFA account
   returns a short-lived challenge token; the code is sent in a second request. A one-shot form
   with the code included is also accepted, for the CLI.
6. **Branch from `4.0.0`.** MFA ships with the 4.0.0 release, not with `develop`, and is
   independent of the plugin work on `plugin-architecture`.

## Phase 1: TOTP on the server and API

### Algorithm

RFC 6238 over HMAC-SHA1, 6 digits, 30 second step, one step of drift either way. Written in-house
(`account/mfa/Totp.kt`, about 50 lines on `javax.crypto.Mac`) and tested against the RFC's test
vectors; no library is worth a dependency for this. Secrets are 20 random bytes, Base32 in the
`otpauth://` URI with `issuer` set to the server's display name and the account email as the label.

Replay protection: the last accepted time step is stored and any code at or before it is refused.

### Schema

New tables in `server/src/main/sqldelight/...`, mirrored in the next `migrations/<N>.sqm`,
verified with `./gradlew :server:verifyMainServerDatabaseMigration`. All foreign keys
`ON DELETE CASCADE` so account purge keeps working.

```
account_totp
  user_id        BIGINT PRIMARY KEY → account
  secret         TEXT     -- encrypted with the active content encryptor, keyed by cipher_secret
  cipher         TEXT     -- ContentEncryptor.cipherName(), for the registry on read
  confirmed      BOOLEAN  -- false while enrollment is pending
  last_step      BIGINT   -- replay guard
  created        TIMESTAMPTZ

account_recovery_code
  id, user_id → account, code_hash TEXT (TokenHasher HMAC), used_at TIMESTAMPTZ NULL

mfa_login_challenge
  id BIGSERIAL, user_id → account, install_id TEXT, token_hash TEXT UNIQUE (TokenHasher),
  attempts INT, expires TIMESTAMPTZ
```

A plaintext-mode server stores the TOTP secret in the clear, the same trade it already makes for
story content. Rotation goes through the registry by cipher tag like every other encrypted column.

### Repository and component

`MfaRepository` (new, `account/mfa/`): begin/confirm/disable TOTP, generate and consume recovery
codes, create/verify/consume login challenges, `hasMfa(userId)`, `resetAll(userId)`.

`AccountsRepository.login` is split so the password check and the token mint are separate steps.
`AccountsComponent.login` becomes:

```
password wrong                     → failure (unchanged)
password right, no MFA             → Token (unchanged)
password right, MFA, no code sent  → MfaRequired(challengeToken, methods)
password right, MFA, code sent     → verify TOTP or recovery code → Token
```

Challenges live five minutes, allow five attempts, and are purged by `TokenMaintenanceJob`. Each
failed code is recorded through `SecurityRepository.recordLoginAttempt` so the existing
brute-force alerts cover it.

Wrong codes also count against the account as a whole, across challenges and the inline path, so
a stolen password cannot be turned into an unbounded guess at a six-digit code: ten failures
within fifteen minutes refuse every code for fifteen minutes. The count and lock live on
`account_totp`; a correct code clears them, and the admin reset lifts the lock. A replayed code
that was already accepted is refused without counting as a guess.

### API

- `POST /api/account/login` accepts an optional `code` form field (TOTP or recovery code) so a
  one-shot login stays possible for the CLI.
- Without it, an MFA account gets `401` with `HttpResponseError(errorCode = "mfa_required",
  mfa = MfaChallenge(token, methods))`. `mfa` is a new nullable field with a null default; the
  network serializer already sets `ignoreUnknownKeys`, so older clients see a plain 401 with a
  display message telling them to update.
- `POST /api/account/login/mfa` with `mfaToken`, `code`, `installId` returns a `Token`. Shares
  `LOGIN_RATE_LIMIT`.

New constants in `base`: `ApiErrorCode.MFA_REQUIRED`, `ApiErrorCode.MFA_CODE_INVALID`,
`MfaChallenge`, `MfaMethod` (`totp`, `recovery`). The `methods` list exists so a later factor can
be added without a protocol change.

### Tests

`TotpTest` (RFC vectors, drift window, replay), `MfaRepositoryTest`, `AccountsComponentLoginTest`
additions (challenge issued, consumed, expired, attempt cap, recovery code single use),
`AccountRoutesTest` for both endpoints and the wire shape of the 401.

## Phase 2: Web enrollment, web login, admin reset

### Dashboard Security section

`SecurityPage.kt` under `frontend/` serves `GET /dashboard/security`, linked from the dashboard's
account area and styled by `security.css`. HTMX partials are `templates/partials/security-*.mustache`.
The page is a list of factors (authenticator app, then recovery codes once it is on), so passkeys
can be added as another entry.

- "Set up authenticator app": `hx-post /dashboard/security/totp/begin` returns a partial with the
  QR code, the manual key and an `otpauth://` link for phones. The QR is rendered server-side as
  inline SVG from `com.google.zxing:core` (BitMatrix rows to `<rect>` runs; no `javase` module, no
  JavaScript, works offline).
- Confirm with one code: `hx-post /dashboard/security/totp/confirm`. On success the factor list is
  re-rendered with the recovery codes shown once, a copy button (the page's only script,
  `security.js`, with a select-the-list fallback where the Clipboard API is missing on plain
  http) and a "download as text" link. The download is a `data:` URI, so the codes are never held
  server-side for a second request.
- Disable: `hx-post /dashboard/security/totp/disable` with the current password and a TOTP or
  recovery code. A wrong code counts toward the account lock.
- Regenerate recovery codes: `hx-post /dashboard/security/recovery-codes/regenerate` with the
  current password.
- Disable and regenerate share `LOGIN_RATE_LIMIT`, since both take the password.

Password confirmation reuses `AccountsRepository.verifyPassword`; the decoy-hash timing defence
does not matter here because the user is already signed in. Enrollment itself asks for no password.

### Web login

`POST /login` with a correct password on an MFA account (that the allowed-users gate would admit)
sets a `PendingMfaSession(userId, expires)` cookie and redirects to `GET /login/verify`. The cookie
uses the same encrypted transport as `UserSession` under keys derived with a different label, so
neither cookie's value is accepted as the other; it is scoped to `/login` with a five-minute
max age, and `expires` is checked against the server clock as well. The password step records no
login attempt; the verify step records the outcome.

`/login/verify` takes a code; "use a recovery code instead" is a link to `?method=recovery` that
swaps the field, so the page needs no script. `POST /login/verify` checks through
`MfaRepository.verifyCode`:

- Verified: the pending cookie is cleared and `UserSession` set, after the same allowed-users check
  as the password path.
- Invalid: the page is shown again and the pending cookie kept, so the user can retry within the
  five minutes. There is no per-cookie attempt cap; the account-wide lock (ten wrong codes in
  fifteen minutes) and the IP rate limit bound guessing.
- Locked: the page says how many minutes to wait. `GET /login/verify` shows the same message.
- Expired cookie, deleted account, or MFA reset while the sign-in waited: back to the login page.

### Admin

The admin users list shows "Reset MFA" for accounts with confirmed TOTP, behind an `hx-confirm`
prompt. It calls `MfaRepository.resetAll`, then `AccountsRepository.forceLogout`, and logs at info
with the admin's user id. `forceLogout` revokes API tokens; existing web `UserSession` cookies are
stateless and stay valid until they expire.

### Tests

End-to-end tests over `WebEndToEndTest` rather than unit-level route tests, so the real templates,
session cookies and database run: `SecurityPageTest` (enrollment partials, disable, regenerate),
`LoginVerifyTest` (redirect, verify page, recovery code, wrong code, lock message, cookie expiry,
reset while pending), `AdminResetMfaTest`, and `MfaWebLoginFlowTest`, which enrolls, logs out and
logs back in with a generated code. `EndToEndTest` takes an overridable `clock` for the expiry and
code-step tests. `QrSvgTest` decodes the rendered SVG with zxing's reader.

## Phase 3: Apps answer the challenge

- `ServerAccountApi.login` turns a 401 whose body carries `errorCode = mfa_required` and an `mfa`
  challenge into `MfaRequiredException(challenge)`, the way a 451 becomes
  `TermsOfServiceRequiredException`. `ServerAccountApi.loginMfa(mfaToken, code, installId)` posts
  to `/api/account/login/mfa`.
- `AccountUseCase.setupServer` returns `ServerSetupResult.MfaRequired(challenge)` and keeps the
  provisional `ServerSettings`, as it does for the terms-of-service challenge. The use case holds
  the challenge and those settings until `completeMfa(code)` stores the token. A refused code
  (`mfa_code_invalid`, which also covers an expired challenge and the account lock) comes back as
  `MfaRequired(challenge, error = <server message>)` so the step stays open; any other failure
  discards the challenge and the provisional settings and returns `Failure`, which sends the
  dialog back to the password step. `cancelMfa()` forgets the challenge; the component owns the
  provisional settings on cancel, as in the terms flow.
- `AccountReauthUseCase.reauthenticate` returns a `ReauthResult` with the same three shapes
  (`Success`, `MfaRequired(challenge, error)`, `Failure(displayMessage)`) and the same
  `completeMfa` / `cancelMfa` pair. Reauth never writes provisional settings, so its failures carry
  the server's display message as a `Msg` instead of the old `ServerSetupFailed` exception.
- `AccountSettings.State` gains `@Transient mfaChallenge`; `ServerReauthentication.State` gains
  the same field. Both components expose `submitMfaCode(code)` and `cancelMfa()`. A blank code
  never leaves the component. While a challenge is pending the account settings component keeps
  the setup dialog open and leaves the pending setup in place so closing the dialog still drops
  the provisional settings.
- `MfaCodeStep` (composeUi, `common/account/`) is shared by `ServerSetupDialog` and
  `ReauthenticationUi`: a digits-only mono `HdHairlineField` with a numeric keyboard and a
  `n/6` counter, or a plain mono field for a recovery code behind a "use a recovery code
  instead" `HdHairlineToggleRow` that only shows when the challenge lists `recovery`. The
  masthead gains a `SECOND FACTOR` stamp, the URL and email fields lock, and the action row
  becomes Back (`cancelMfa`) and Verify (`submitMfaCode`); the masthead close still cancels the
  whole setup. `HdHairlineField` gained an optional `textStyle` for the mono input. The digit
  count is `MfaMethod.TOTP_DIGITS` in `base`, which the server's `Totp.DIGITS` now reads.
- Strings live in `strings_account_settings.xml` under `settings_server_setup_mfa_*` and are
  shared by both dialogs.
- The `operations` module (`account.login` with an optional `code`) is not on the 4.0.0 line yet;
  that part waits for the branch that carries it. The server already accepts the inline `code`
  form field, so it needs no API change.

Tests: `ServerAccountApiTest` for the 401 shapes and `loginMfa`, `AccountUseCaseTest` and
`AccountReauthUseCaseTest` for the result ladder, `AccountSettingsComponentTest` and
`ServerReauthenticationComponentTest` for the state transitions.

## Pull request plan

| PR | Contents |
|---|---|
| 1 | `base` types, TOTP, `MfaRepository`, schema migration, login split, API endpoints, tests |
| 2 | Dashboard Security section, `/login/verify`, admin reset, i18n |
| 3 | Client challenge step in setup, reauth, and `operations` |

PRs 1 to 3 ship TOTP end to end.

## Later, optional

- **Require MFA for all accounts** as an admin setting.
- **Trusted browsers** ("don't ask again here for 30 days") on the web login.
- **In-app TOTP enrollment**, rendering the QR with `io.github.alexzhirkevich:qrose`.

## Shelved: passkeys

Kept here so the TOTP work stays compatible with it. Nothing in this section is scheduled.

### Policy

A passkey counts as both factors when the authenticator verified the user (PIN or biometric), so
it can sign in on its own, on the web and in the apps. As a second factor after a password, user
verification is preferred but not required. The apps' sign-in stays fully native: no browser is
opened at any point. Each platform uses its own passkey API, and the server speaks plain WebAuthn
JSON to all of them.

### Library

`com.yubico:webauthn-server-core` (java-webauthn-server, Apache 2.0, latest stable 2.7.x, 2.8
in alpha). Well-documented `RelyingParty` API; we implement `CredentialRepository` over a new
DAO. Pulls in Jackson for CBOR and JSON, which is new to the server but contained. The alternative
is `com.webauthn4j:webauthn4j-core` 0.29.x, the Keycloak choice; either works, Yubico's docs are
better for a one-person team.

Passkeys need `ServerConfig.publicUrl` set to an `https` origin: the relying party id is its
host and the allowed origin is the URL itself. Without it the Security section hides passkeys
and the login page hides the passkey button. `localhost` over `http` is allowed for development.

### Schema

```
passkey_credential
  id BIGSERIAL, user_id → account, credential_id BYTEA UNIQUE, public_key_cose BYTEA,
  sign_count BIGINT, transports TEXT, aaguid TEXT, label TEXT, discoverable BOOLEAN,
  backed_up BOOLEAN, created TIMESTAMPTZ, last_used TIMESTAMPTZ NULL
```

Ceremony challenges are short-lived (two minutes) and held in a Kache cache keyed by a random
id. The server is single-instance, so no table is needed. Admin "Reset MFA" also clears this table.

### Registration

Security section: "Add a passkey" posts to `/dashboard/security/passkeys/options`, runs
`navigator.credentials.create()` in a small `assets/js/passkeys.js` (uses
`PublicKeyCredential.parseCreationOptionsFromJSON` and `toJSON()` with a shim for older
browsers), then posts the result to `/dashboard/security/passkeys/register`. Resident key
preferred, user verification preferred. Each passkey gets a label and can be removed.

### Web login

- Second factor: `/login/verify` gets "Use a passkey", calling `navigator.credentials.get()`
  with the user's credentials in `allowCredentials`. `MfaMethod` gains `passkey`.
- Passwordless: `/login` gets "Sign in with a passkey", a discoverable-credential request with
  `userVerification = required`. A verified assertion signs the user in with no password and no
  further step. Conditional UI (autofill) is a nice-to-have.
- Sign counter regressions are logged and rejected, per the spec.

Tests: `CredentialRepository` over the DAO, option generation, and rejection of a malformed or
wrong-origin assertion. The library's own test vectors cover the cryptography; a browser e2e
covers the ceremony if the e2e harness can drive a virtual authenticator (Chrome DevTools
protocol supports one).

### API for the apps

Same ceremonies as the web, over the API instead of the session cookie:

- `POST /api/account/login/passkey/options` with `email` (optional, for `allowCredentials`) and
  `installId` returns `PublicKeyCredentialRequestOptions` JSON; the challenge is cached for two
  minutes.
- `POST /api/account/login/passkey` with the assertion JSON and `installId` returns a `Token`.
  A user-verified assertion is a complete login; an unverified one answers a pending
  `mfa_login_challenge` instead (`MfaMethod.passkey`).
- `POST /api/account/passkeys/options` and `POST /api/account/passkeys/register` under bearer
  auth, for enrolling from inside an app.

The client gets an `expect class PasskeyAuthenticator` in `common` with `register(optionsJson)`
and `authenticate(optionsJson)`, both returning the WebAuthn JSON the server consumes, plus
`isAvailable()` so the button only shows where it can work. One `actual` per platform.

### Platform constraints

| Platform | API | Works with any self-hosted server? |
|---|---|---|
| Android | Credential Manager (`androidx.credentials`), Play services or another provider | Yes. The provider fetches `https://<rpId>/.well-known/assetlinks.json` at call time, so the server serves it from config, listing the Play and F-Droid signing certificate fingerprints. The assertion's origin is `android:apk-key-hash:<hash>`, which the server adds to the relying party's allowed origins. |
| iOS | `ASAuthorizationPlatformPublicKeyCredentialProvider`, called from Kotlin/Native directly | No. Apple requires the relying party domain in the app's Associated Domains entitlement, fixed at build time, plus `apple-app-site-association` on the server. The store build can list `hammer.ink`; any other server gets no native passkeys on iOS. |
| Windows | `webauthn.dll` (Windows Hello and roaming keys) over JNA, needs the Compose window's HWND | Yes. The app builds `clientDataJSON` itself with `https://<host>` as origin; no domain registration. Windows 10 1903 or later. |
| macOS | Same AuthenticationServices provider as iOS, reached from the JVM through a small Swift helper process or Objective-C runtime calls | No, same entitlement rule as iOS, and the jpackage bundle must be signed with it. `hammer.ink` only. |
| Linux | No platform authenticator. Hardware keys only, through `libfido2` over JNA (CTAP2 over USB HID) | Yes, for security keys. No synced passkeys. |

F-Droid builds of the Android app are signed with a different key than Play, so the served
assetlinks file lists both fingerprints, and the server's allowed origins include both key hashes.

### Order of work, if resumed

1. Server ceremonies and `.well-known` files, shared by every platform.
2. Android, the cleanest platform and the one with the most users.
3. Windows.
4. iOS and macOS, limited to domains in the entitlement.
5. Linux security keys.

Where `isAvailable()` is false the app shows only the authenticator-code step, so TOTP remains the
floor on every platform.
