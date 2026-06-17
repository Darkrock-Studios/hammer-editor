# Encryption at rest & key management (server admin guide)

Hammer is offline-first and stores everything in PostgreSQL. **Content
encryption at rest is optional.** This guide explains the keyring, how to turn
encryption on or off, how to rotate keys, and how to safely delete an old key.

CLI examples assume the packaged server (`./server` / `server.bat`); pass
arguments through `--args`, e.g. `./server --args="generate-keyring"`.

## The short version

- A brand-new server with no encryption config stores **plaintext**. It just
  works — no keys to manage.
- To encrypt content at rest you create a **keyring**, point the server at it,
  and set `mode = "aes"`. On the next start the server re-encrypts existing data
  before serving (a one-time maintenance window).
- Turning encryption off, or rotating a key, is the same shape: change config /
  keyring, restart, the server converges the data before serving.
- All key generation and rotation is **offline** (CLI subcommands). The running
  server never creates or changes key material on its own.

## The keyring

Key material lives in a single JSON document, the **keyring**. It holds two
independent roles:

- **`content`** — derives the per-user keys that encrypt entity content and
  review snapshots.
- **`tokenHmac`** — hashes authentication tokens.

Each role has versioned keys (`v1`, `v2`, …) and an `active` key used for new
writes. Splitting the roles means you can retire the content key (after
decrypting everything) without forcing every user to log in again.

> **Keep the keyring separate from the database.** The point of encryption is
> that a stolen database backup is useless without the key. If the keyring sits
> in the same backup as `pgdata/`, you get no protection. Store it somewhere the
> data backups don't reach (a secrets manager, a separate mount, etc.).
>
> **Losing a key is not symmetric.** Lose the **content** key and the encrypted
> data is unrecoverable. Lose the **tokenHmac** key and users simply have to log
> in again. Back up the content key accordingly.

## Configuration

Two optional config blocks in `serverConfig.toml`:

```toml
# What new writes use. Omit the block entirely on a fresh server to store plaintext.
[encryption]
mode = "aes"   # "aes" to encrypt, "none" to store plaintext

# Where the keyring is read from. Defaults to the file provider.
[secret]
provider = "file"                          # "file" or "env"
file = "/etc/hammer/server.keyring.json"   # file provider; default: ~/hammer_data/server.keyring.json
envVar = "HAMMER_KEYRING"                   # env provider; the variable holds the keyring JSON
```

`mode` has three states:

| `mode` | Meaning |
|---|---|
| omitted (unspecified) | Plaintext on a fresh server. **If the database already holds encrypted rows, the server refuses to start** until you choose `aes` or `none` — so an upgrade can't silently downgrade your data. |
| `"aes"` | Encrypt new writes; converge existing data to the active content key. Requires a keyring. |
| `"none"` | Store plaintext; converge existing encrypted data to plaintext. |

Reads always work regardless of `mode`: every row records which cipher (and key
generation) it was written with, so mixed data decrypts correctly during a
convergence.

## Generating a keyring

```bash
# Print a fresh keyring to stdout…
./server --args="generate-keyring"

# …or write it straight to the default location.
./server --args="generate-keyring --out ~/hammer_data/server.keyring.json"
```

This mints random keys for both roles (`v1`, active `v1`). Put the file where
your configured provider reads it (default `~/hammer_data/server.keyring.json`),
or for the env provider, set the variable to the JSON contents:

```toml
[secret]
provider = "env"
envVar = "HAMMER_KEYRING"
```

Inspect a keyring without revealing key bytes:

```bash
./server --args="inspect-keyring"            # default path
./server --args="inspect-keyring --in /etc/hammer/server.keyring.json"
```

## Enabling encryption (plaintext → AES)

1. Generate a keyring and place it for your provider (above).
2. Set `mode = "aes"` in `serverConfig.toml`.
3. (Optional but recommended) Dry-run first — see [Previewing](#previewing-a-convergence).
4. Restart the server. Before accepting traffic it re-encrypts every content row
   to the active key, logging progress. On a large database this is a
   maintenance window proportional to data size.

Subsequent restarts skip the scan (a marker records the last-applied target), so
normal boots are fast.

## Disabling encryption (AES → plaintext)

1. Set `mode = "none"`.
2. Restart. The server decrypts every row to plaintext before serving.
3. Once convergence completes, the content key is **provably unused** and can be
   deleted from the keyring (see [Deleting an old key](#deleting-an-old-key)).

## Rotating a key

Rotation is offline: add a new key generation, restart, let convergence move the
data onto it.

```bash
# Add v2 to the content role and make it active; keeps v1 so existing rows still read.
./server --args="rotate-key --role content --in ~/hammer_data/server.keyring.json --out ~/hammer_data/server.keyring.json"
```

Then restart with `mode = "aes"`. Convergence re-encrypts every content row from
the old generation onto `v2`. When done, the old generation is unused and can be
removed.

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
would exceed the size cap once encrypted (those would block convergence; shrink
or split them first). Exit code `0` means convergence would complete; `1` means
there are over-cap rows to deal with.

## Deleting an old key

After a disable (→ plaintext) or a rotation (→ new generation), the previous
content key is no longer referenced by any row. Convergence runs to completion
before the server serves, so once a converged boot has finished, **no row is
left on the old key**. At that point it's safe to remove that key generation
from the keyring's `content.keys` and keep a backup elsewhere if you might need
to read an old database snapshot.

Use `inspect-keyring` to see which generations exist before pruning.

## Upgrading an existing (already-encrypted) server

Older releases always encrypted, using an auto-generated
`~/hammer_data/server.secret`. After upgrading:

- That `server.secret` is read automatically as the keyring's `content.v1` and
  `tokenHmac.v1` — **existing data keeps decrypting and existing logins keep
  working**, no action required for the keys themselves.
- But because the default `mode` is now unspecified, a server that holds
  encrypted data **will refuse to start until you set `mode` explicitly**. Set
  `mode = "aes"` to keep encrypting (recommended for an existing encrypted
  server), or `mode = "none"` to deliberately decrypt everything to plaintext.

If you'd rather manage an explicit keyring file than rely on the legacy
`server.secret`, copy its **exact** contents into the `content.v1` and
`tokenHmac.v1` values of a keyring document and place that for your provider,
then remove `server.secret`. Do **not** run `generate-keyring` for this — that
mints new keys and would leave the existing data unreadable. (Alternatively,
keep the grandfathered keyring and rotate to a fresh generation once; let
convergence move the data onto it.)
