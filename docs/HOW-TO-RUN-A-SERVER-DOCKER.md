# How to run a Sync Server with Docker

A slim, non-root image for self-hosting the Hammer sync server. By default it
uses the in-process (embedded) PostgreSQL database, so no external services are
required — just a volume for your data.

This is the Docker-specific guide. For everything that is not Docker-specific —
whitelisting, email, community, analytics, encryption at rest — see
[HOW-TO-RUN-A-SERVER.md](HOW-TO-RUN-A-SERVER.md). Where the two disagree about
networking, **this document wins for containers**.

> Running a server reachable on the internet is an inherently technical task. If
> you have never done it, this is probably not for you.

## Quick start

```bash
cd docker
docker compose up -d
```

That pulls `ghcr.io/darkrock-studios/hammer-editor/server:latest`, keeps all
durable state in the `hammer-data` volume, and serves plain HTTP on
`127.0.0.1:8080`.

Or without compose:

```bash
docker run -d --name hammer-server \
  -p 127.0.0.1:8080:8080 \
  -v hammer-data:/data \
  ghcr.io/darkrock-studios/hammer-editor/server:latest
```

Then **download a client and create an account** — the first account created
becomes the admin account.

## Networking

The container serves **plain HTTP** on port 8080. Hammer clients only speak
HTTPS, so that port is never what clients talk to directly. Two supported shapes:

- **Reverse proxy (recommended):** a proxy (Nginx, Caddy, Traefik) terminates TLS
  and forwards plain HTTP to the container.
- **Hammer terminates TLS:** mount a certificate, set `sslCert`, publish 443.

Both quick starts above publish to `127.0.0.1` deliberately. Docker installs its
own iptables rules that **bypass host firewalls like ufw and firewalld**, so
publishing to `0.0.0.0` puts the cleartext port on the public internet even when
the host firewall says otherwise — exposing passwords, tokens, and sync traffic.

To change the host-side binding or port:

```bash
HAMMER_HTTP_BIND=0.0.0.0 HAMMER_HTTP_PORT=8090 docker compose up -d
```

### Do not set `bindHosts`

[HOW-TO-RUN-A-SERVER.md](HOW-TO-RUN-A-SERVER.md#reverse-proxy-using-nginx) tells
reverse-proxy operators to set `bindHosts = ["127.0.0.1", "::1"]`. **That advice
does not apply to containers.** Inside a container it binds the *container's* own
loopback, so the published port forwards to an address nothing is listening on:
every request fails, while the container still logs a clean startup and reports
healthy. Leave `bindHosts` unset and restrict exposure on the host side instead.

## Configuration

The server auto-loads `config.toml` from its data directory
(`/data/hammer_data/config.toml`) — no `--config` flag needed. Start from
[config.example.toml](../docker/config.example.toml); everything in it is
optional.

Two ways to provide it:

- **Named volume (default):** uncomment the config bind mount in
  `docker-compose.yml`. This works because the image ships a `hammer`-owned
  `/data/hammer_data`, so the file mounts cleanly on top.
- **Host bind-mounted data dir:** if you mount a host directory at `/data`
  instead, place the file at `<hostdir>/hammer_data/config.toml` directly. Don't
  bind-mount the single file in that case — Docker would create the parent as
  root. Make sure the directory is owned by uid/gid `1000`.

A bad config aborts startup rather than silently falling back to defaults, so
check `docker logs` if the container exits immediately.

Paths inside `config.toml` are resolved differently depending on the setting:
`termsOfService` and `privacyPolicy` resolve relative to the config file, but
**TLS certificate paths do not** — give those absolute container paths.

> **Windows users:** save `config.toml` as UTF-8 **without** a BOM. Notepad and
> PowerShell's `Out-File -Encoding utf8` add one, and the TOML parser rejects it
> with a confusing `UnexpectedTokenException` on line 1. In PowerShell use
> `[System.IO.File]::WriteAllText($path, $text)`.

## TLS in the container

If you want Hammer itself to terminate TLS, mount the certificate directory and
publish 443:

```yaml
    ports:
      - "443:443"
    volumes:
      - hammer-data:/data
      - /etc/letsencrypt:/certs:ro
```

```toml
sslPort = 443

[sslCert]
certChainPath = "/certs/live/hammer.example.com/fullchain.pem"
privateKeyPath = "/certs/live/hammer.example.com/privkey.pem"
```

**Renewals need a restart.** The server reads its certificate only at startup, so
a renewed certificate is not picked up until the container restarts. Mount the
live certificate directory (as above) rather than copying PEMs into the volume,
and restart the container after each renewal — e.g. as a certbot deploy hook:

```ini
# /etc/letsencrypt/renewal/hammer.example.com.conf, under [renewalparams]
deploy_hook = docker restart hammer-server
```

Without this, sync silently stops for every user about 90 days in, when clients
begin rejecting the expired certificate.

Self-signed certificates are not supported: mobile clients trust only the system
CA store. Use a real certificate, or a reverse proxy holding one.

## Using an external PostgreSQL

The default is an in-process PostgreSQL inside the container — nothing else to
run, and the right choice for most self-hosters. To point at an externally
managed database, switch `config.toml` to remote storage:

```toml
[storage]
type = "remote"

[storage.remote]
host = "postgres"
port = 5432
database = "hammer"
user = "hammer"
password = "change-me"
useSsl = false
```

The schema is created and migrated automatically on first connect, so an empty
database is all that's needed. `docker-compose.yml` contains a commented-out
`postgres` service (plus the matching `depends_on`) to uncomment for this; `host`
must match that service's name.

> **`useSsl` defaults to `true`.** A stock `postgres` container serves no TLS, so
> leaving it unset against one fails to connect. Set `useSsl = false` for a local
> sidecar; keep it on for a managed database that terminates TLS.

In remote mode the `/data` volume still holds the caches and keyring, but no
`pgdata` — back up your PostgreSQL server instead.

## Data & backups

Everything durable lives under the `/data` volume:

```
/data/hammer_data/
  pgdata/                 embedded PostgreSQL data
  cache/                  regenerable render/OpenGraph caches
  config.toml             your config (if you put it here)
  server.keyring.json     encryption keyring (only if encryption is enabled)
```

Back up the volume to back up the server. If you use a host bind mount instead of
a named volume, make sure the directory is writable by uid/gid `1000` (the
image's `hammer` user):

```bash
sudo chown -R 1000:1000 /path/to/your/data
```

## Admin CLI subcommands

The image entrypoint is the server launcher, so subcommands work by appending
them to `docker run` with the data volume mounted:

```bash
docker run --rm -v hammer-data:/data \
  ghcr.io/darkrock-studios/hammer-editor/server:latest \
  generate-keyring --out /data/hammer_data/server.keyring.json
```

Two rules matter in a container:

**Anything meant to persist needs `--out` pointing into the volume.** These
commands print to stdout by default, and with `--rm` that output is all you get.

**Commands that read the database need the server stopped** — but only with the
default embedded storage. The embedded PostgreSQL holds an exclusive lock on
`pgdata`, so a second container cannot open it while the server is running:

```
Could not read the database to verify which content keys are in use:
could not lock /data/hammer_data/pgdata/epg-lock
```

This fails safely — nothing is written or corrupted — but the command does not
run. Stop the server first, then start it again afterwards:

```bash
docker stop hammer-server
docker run --rm -v hammer-data:/data \
  ghcr.io/darkrock-studios/hammer-editor/server:latest \
  prune-key --role content --config /data/hammer_data/config.toml --dry-run
docker start hammer-server
```

| Command | Needs the database | Server must be stopped |
|---------|--------------------|------------------------|
| `generate-keyring` | no | no |
| `rotate-key` | no | no |
| `inspect-keyring` | no | no |
| `migrate-secret` | no | no |
| `prune-key --role tokenHmac` | no | no |
| `prune-key --role content` | yes | yes (embedded storage) |
| `--converge-dry-run` | yes | yes (embedded storage) |

The lock is specific to the embedded database. With `[storage] type = "remote"`
the database is a separate service, so these commands can run against a live
server.

### Rotating an encryption key

Key rotation itself never touches the database. Rotate offline, write the result
into the volume, then restart — the server reads its keyring only at startup, so
a rotated keyring has no effect until the container restarts:

```bash
# Review the rotated keyring first (prints to stdout, writes nothing)
docker run --rm -v hammer-data:/data \
  ghcr.io/darkrock-studios/hammer-editor/server:latest rotate-key --role content

# Then write it and restart to pick it up
docker run --rm -v hammer-data:/data \
  ghcr.io/darkrock-studios/hammer-editor/server:latest \
  rotate-key --role content --out /data/hammer_data/server.keyring.json
docker restart hammer-server
```

Back up the existing keyring before overwriting it. Losing key material means
losing the content encrypted under it — see
[SERVER-SECRET-STORAGE.md](SERVER-SECRET-STORAGE.md).

Pruning old generations comes *after* the server has converged existing rows onto
the new key, and needs the server stopped as described above.

## Building the image yourself

The image packages the pre-built server distribution rather than compiling from
source (the server shares the `:base` module with the Android client, so a source
build needs the Android SDK). From the repo root:

```bash
./gradlew :server:installDist
docker build -f docker/Dockerfile -t hammer-server .
```
