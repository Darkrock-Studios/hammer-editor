# Encryption at rest & key management (server admin guide)

The Hammer sync server stores user data in PostgreSQL. **Encrypting the User content
at rest in the database is optional.** _Self-hosted single user instances are advised to not
use encryption._ Encryption makes syncing slower, and has the potential for total user
data loss if the key materials are mishandled. If you do choose to use encryption, then read on.

# ⚠️⚠️🚨🚨 **EXISTING SERVERS _MUST READ_
** [Upgrading existing server](#upgrading-an-existing-already-encrypted-server) 🚨🚨⚠️⚠️

## Why encrypt

Hammer is not designed to be a high security application, but it is designed to provide basic
privacy
for user data. A properly configured multi-user sync server should have two things configured:

- SSL for transport encryption
- AES encryption for user's story content at rest

This prevents the two major threat vectors to user data being compromised:

- Capture in transit from client to server
- Database Capture in case of server compromise

If an attacker sniffs your connection, or steals the server database, user's stories will still be
protected.

This is explicitly _not_ trying to provide end-to-end encryption or anything like it. Again, it's
all about basic user data protection.

## The short version

A brand-new server with no encryption configured stores user content in **plaintext**. It just
works, no keys to manage, and no possibility of losing key material, resulting in total user data
loss.

- To manage the server's encryption, always shut down the running server process and
  perform maintenance with it offline.
- To enable encryption, create a **keyring**, point the server at it,
  and set `mode = "aes"`. On the next start the server will re-encrypt existing data
  before serving (_a one-time maintenance window_).
- Turning encryption off, or rotating a key, is the same shape: change config /
  keyring, restart, the server converges the data before serving.
- All key generation and rotation is **offline** (CLI subcommands). The running
  server never creates or changes key material on its own.

The rest of this doc goes into detail on how to do all of that.

## A note on this doc

CLI examples assume the packaged server (`./server` / `server.bat`); pass
arguments through `--args`, e.g. `./server --args="generate-keyring"`.

## The keyring

Key material lives in a single JSON document, the **keyring**. It holds two
independent roles:

- **`content`** — derives the per-user keys that encrypt entity content and
  review snapshots.
- **`tokenHmac`** — hashes authentication tokens.

> **Keep the keyring separate from the database.** The point of encryption is
> that a stolen database backup is useless without the key. If the keyring sits
> in the same backup as `pgdata/`, you get no protection. Store it somewhere the
> data backups don't reach (a env var, a separate mount, etc.).
>
> **Losing a key is not symmetric.** Lose the **content** key and the encrypted
> data is unrecoverable. Lose the **tokenHmac** key and users simply have to log
> in again. Back up the content key accordingly.

## Configuration

There are two optional config blocks in `serverConfig.toml`:

```toml
# Omit the block entirely on a fresh server to store plaintext.
[encryption]
mode = "aes"   # "aes" to encrypt, "none" to store plaintext

# Where the keyring is read from. There are two options

# "env" is recomended for most self hosters
[secret]
provider = "env"
envVar = "HAMMER_KEYRING"                   # the variable holds the keyring JSON

# "file" is useful in docker, allowing you to point to a mount
[secret]
provider = "file"                          # "file" or "env"
# DO NOT USE THE DEFAULT!
file = "/etc/hammer/server.keyring.json"   # default: ~/hammer_data/server.keyring.json
# Store the keyring some where else, not next to the database!
```

`mode` has three states:

| `mode`                  | Meaning                                                                                                                                                                                             |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| omitted (_unspecified_) | Plaintext on a fresh server. **If the database already holds encrypted rows, the server will refuse to start** until you choose `aes` or `none` — so an upgrade can't silently downgrade your data. |
| `"aes"`                 | Encrypt new writes; converge existing data to the active content key. Requires a keyring.                                                                                                           |
| `"none"`                | Store plaintext; converge existing encrypted data to plaintext.                                                                                                                                     |

_Note: Reads always work regardless of `mode`: every row records which cipher (and key
generation) it was written with, so mixed data decrypts correctly during a
convergence._

## Generating a keyring

```bash
# Print a fresh keyring to stdout…
./server --args="generate-keyring"

# …or write it straight to the default location.
./server --args="generate-keyring --out ~/hammer_data/server.keyring.json"
```

This mints random keys for both roles. Now copy them, into your chosen key provider:

- Env Var:
	- For the env provider, set the variable to the JSON contents
	- If hosting on linux, here are some options:
		- _Do not_ put it in your **.bashrc**! `export HAMMER_KEYRING=… in ~/.bashrc/~/.profile`
		  This will leak the key materials into every interactive session.
		- **systemd (bare-metal/VM):** put it in a dedicated file referenced by `EnvironmentFile=`:
	  ```
	  # /etc/hammer/hammer.env  (root:root, chmod 600)`
	  HAMMER_KEYRING={"schema":1,"content":{...},"tokenHmac":{...}}`
	  # (No quotes needed — systemd takes the rest of the line literally, which is why the single-line JSON matters.)
	  ```
	  Then reference the new file in the systemd unit file:
	  ```
      # /etc/systemd/system/hammer.service
	  [Service]
	  EnvironmentFile=/etc/hammer/hammer.env
	  ``` 
		- **Docker Compose:** use env_file: pointing at a `0600` file, or better, a Docker secret +
		  the file provider (secrets mount as a file at `/run/secrets/…`, which avoids env
		  entirely). Note docker inspect exposes plain environment: values.
		- **Kubernetes:** a Secret injected via `env.valueFrom.secretKeyRef` is the natural durable
		  fit — k8s persists it and re-injects on every pod restart.

- File:
	- Put the file where your configured provider reads it (⚠️ _Don't put it next to the
	  database!_),

### Inspect a keyring without revealing key bytes:

By default, it reads the keyring from your config's `[secret]` provider —
env or file, exactly as the server would (including a grandfathered legacy
`server.secret`):

```bash
./server --args="inspect-keyring --config /path/to/serverConfig.toml"
```

Or point it straight at a file, which overrides the provider:

```bash
./server --args="inspect-keyring --in /etc/hammer/server.keyring.json"
```

## Enabling encryption (plaintext → AES)

1. Shutdown the sync server.
2. Generate a keyring and place it for your provider (above).
3. Set `mode = "aes"` in `serverConfig.toml`.
4. (Optional but recommended) Dry-run first — see [Previewing](#previewing-a-convergence).
5. Start the server. Before accepting traffic it re-encrypts every content row
   to the active key, logging progress. On a large database this is a
   maintenance window proportional to data size.

Subsequent restarts skip the scan (a marker records the last-applied target), so
normal boots are fast.

## Disabling encryption (AES → plaintext)

1. Shutdown the sync server.
2. Set `mode = "none"`.
3. Start the server. The server decrypts every row to plaintext before serving.
4. Once convergence completes, the content key is **provably unused** and can be
   deleted from the keyring (see [Deleting an old key](#deleting-an-old-key)).

## Rotating a key

Rotation is offline: add a new key generation, restart, let convergence move the
data onto it.

1. Shutdown the sync server.
2. Read the current keyring from your config's provider, add `v2` to the content
   role, make it active (keeps `v1` so existing rows still read), and write the
   result out:
   ```bash
   ./server --args="rotate-key --role content --config /path/to/serverConfig.toml --out ./server.keyring.json"
   ```
   (Use `--in <file>` instead of `--config` to rotate a specific keyring file.)
3. Place the rotated keyring for your provider (write the file, or update the env
   var to its contents).
4. Start the server with `mode = "aes"`. Convergence re-encrypts every content
   row from the old generation onto `v2`. When done, the old generation is unused
   and can be removed.

Rotating the **`tokenHmac`** role (`--role tokenHmac`) instead invalidates all
existing sessions — every user re-logs in on the next start. Content is
untouched. There's no data convergence for token rotation.

## Previewing a convergence

Before a real run, see exactly what would happen — without writing anything or
binding a port:

```bash
./server --args="--converge-dry-run"
```

It reports how many rows are off the configured target and flags any entity that
would exceed the size cap once encrypted (_those would block convergence; this should
essentially never happen._). Exit code `0` means convergence would complete; `1` means
there are over-cap rows to deal with.

## Deleting an old key

After a rotation, the old content generation lingers in the keyring so existing
rows still decrypt. Once convergence has moved every row onto the new key, that
old generation is dead weight — but deleting the *wrong* generation by hand
destroys data. `prune-key` removes the right ones for you.

It reads the keyring (from your config's provider, or `--in <file>`), checks the
database to see which content generations still protect rows, and drops every
**non-active** generation that has **zero** rows left on it. The active
generation is never removed.

```bash
# Preview first — reports what it would drop and keep, writes nothing.
./server --args="prune-key --role content --config /path/to/serverConfig.toml --dry-run"

# Prune unused content generations and write the result out.
./server --args="prune-key --role content --config /path/to/serverConfig.toml --out ./server.keyring.json"
```

Then place the pruned keyring for your provider (write the file, or update the
env var) and restart.

- **A generation still has rows on it?** It's kept and reported (e.g.
  `Kept (rows still encrypted with them — run convergence first): v1`). Run a
  converged boot first, then prune again — pruning never half-strands data.
- **Target one generation** with `--key v1`. Unlike the sweep, this *fails* (exit
  `1`, writes nothing) if `v1` is the active generation or still has rows, so an
  explicit request can't silently no-op.
- **`--in <file>`** prunes a specific keyring file instead of the provider's, but
  `--config` is still required for the content role — that's where the database
  connection comes from.

Pruning the **`tokenHmac`** role (`--role tokenHmac`) needs no database: only the
active token key ever verifies tokens, so every non-active token generation is
already dead and is dropped immediately.

Use `inspect-keyring` to see which generations exist before and after pruning.

## Upgrading an existing (already-encrypted) server

Older releases always encrypted, (pre v3.3.1) using an auto-generated
`~/hammer_data/server.secret`. After upgrading:

- That `server.secret` is read automatically as the keyring's `content.v1` and
  `tokenHmac.v1` — **existing data keeps decrypting and existing logins keep
  working**, no action required for the keys themselves.
- But because the default `mode` is now unspecified, a server that holds
  encrypted data **will refuse to start until you set `mode` explicitly**. Set
  `mode = "aes"` to keep encrypting (_recommended for an existing encrypted
  server_), or `mode = "none"` to deliberately decrypt everything to plaintext.

### Moving to an explicit keyring (*strongly recommended*)

We recommend you migrate your `server.secret` to a keyring. Support for the
grandfathered secret will eventually be removed. When migrating **do not hand-copy it!**
`server.secret` is read verbatim (every byte, including any trailing newline) and
may contain non-ASCII bytes, so a manual copy that trims or re-encodes it produces a
different key and leaves your data unreadable. Use `migrate-secret`, which reads the file exactly
as the server does and emits a matching keyring:

_Do **not** run `generate-keyring` for this — that mints new random keys and
would leave existing data unreadable._

#### Secret migration and upgrade:
1. Shut the server down
2. Edit your `serverConfig.toml`

```
[encryption]
mode = "aes"
```

3. Migrate the grandfathered `server.secret` to a keyring

```bash
# Reads ~/hammer_data/server.secret and writes a keyring whose content.v1 and
# tokenHmac.v1 are that secret, byte-for-byte. (pass --in to override the path.)
./server --args="migrate-secret --out ./server.keyring.json"
```

4. Edit your `serverConfig.toml` and select the file provider (NOT env var yet!)

```toml
provider = "file"
file = "~/hammer_data/server.keyring.json" # Just during migration
```

5. Start the server again. The server will now read the keyring instead of grandfathering,
   with identical results. Once it boots cleanly, remove `server.secret`.
6. The servers crypto is now migrated to the keyring provider and everything is working!
7. Shut down the server once again
8. The legacy keys should be immediately rotated! The grandfathered `v1` values are the
   old `server.secret` bytes — often non-ASCII and not safe to put in an env var. Rotate
   both roles to fresh Base64-ASCII keys, writing back to the keyring file your provider reads:

```bash
# Reads the current keyring via the config's provider, adds a fresh active key,
# and writes the whole keyring back out. Run content first, then tokenHmac.
./server --args="rotate-key --role content   --config /path/to/serverConfig.toml --out ~/hammer_data/server.keyring.json"
./server --args="rotate-key --role tokenHmac --config /path/to/serverConfig.toml --out ~/hammer_data/server.keyring.json"
```

_Rotating `tokenHmac` invalidates active sessions, every user must re-log in on the next start._

9. Start the server to trigger a convergence, all data will be re-encrypted
10. Once the server data converges and finishes booting, shut it down one last time
11. Now prune the grandfathered keys. Convergence (step 8) moved every content row onto
    the new key, so the old `content` generation is unused and safe to drop; the old
    `tokenHmac` generation needs no database check. Prune both, writing the result back:

```bash
./server --args="prune-key --role content   --config /path/to/serverConfig.toml --out ~/hammer_data/server.keyring.json"
./server --args="prune-key --role tokenHmac --config /path/to/serverConfig.toml --out ~/hammer_data/server.keyring.json"
```

12. Everything is set for you to now select a more secure secret storage method. Either
    move the file to a different directory not captured in backups, or switch to "env"
    storage.
13. Once your new secret storage is configured, start the server one last time. You're all set! 🥳
