# Running the Hammer server with Docker

A slim, non-root image for self-hosting the Hammer sync server. By default it
uses the in-process (embedded) PostgreSQL database, so no external services are
required — just a volume for your data.

> Running a server that's reachable on the internet is an inherently technical
> task. For the full picture (SSL, reverse proxies, whitelisting, email,
> analytics, …) see [../docs/HOW-TO-RUN-A-SERVER.md](../docs/HOW-TO-RUN-A-SERVER.md).

## Quick start (Docker Compose)

```bash
cd docker
docker compose up -d
```

This pulls `ghcr.io/darkrock-studios/hammer-editor/server:latest`, stores all
durable state in the `hammer-data` volume, and serves plain HTTP on port 8080.

Then **download a client and create an account** — the first account created
becomes the admin account.

## Quick start (docker run)

```bash
docker run -d --name hammer-server \
  -p 8080:8080 \
  -v hammer-data:/data \
  ghcr.io/darkrock-studios/hammer-editor/server:latest
```

## TLS

Hammer clients only speak HTTPS. Two supported shapes:

- **Reverse proxy (recommended):** Terminate TLS at a proxy (Nginx, Caddy,
  Traefik, …) in front of the container and forward plain HTTP to port 8080.
  Do not expose 8080 directly to the internet in this case.
- **Hammer terminates TLS:** Mount your certificate into the container, set
  `sslCert` in `config.toml`, and publish 443 as well (`-p 443:443`).

See [config.example.toml](config.example.toml) and the main server doc for
details.

## Configuration

The server auto-loads `config.toml` from its data directory
(`/data/hammer_data/config.toml`). Provide one by either:

- Mounting it (uncomment the bind mount in `docker-compose.yml`), or
- Copying it into the volume after first boot.

Start from [config.example.toml](config.example.toml). Everything in it is
optional — the server runs with sane defaults out of the box.

## Data & backups

Everything durable lives under the `/data` volume:

```
/data/hammer_data/
  pgdata/                 embedded PostgreSQL data
  cache/                  regenerable render/OpenGraph caches
  config.toml             your config (if you put it here)
  server.keyring.json     encryption keyring (only if encryption is enabled)
```

Back up the volume to back up the server. If you use a host bind mount instead
of a named volume, make sure the directory is writable by uid/gid `1000` (the
image's `hammer` user):

```bash
sudo chown -R 1000:1000 /path/to/your/data
```

## Building the image yourself

The image packages the pre-built server distribution rather than compiling from
source (the server shares the `:base` module with the Android client, so a
source build needs the Android SDK). Build the distribution first, then the
image, from the repo root:

```bash
./gradlew :server:installDist
docker build -f docker/Dockerfile -t hammer-server .
```

## Admin CLI subcommands

The image entrypoint is the server launcher, so subcommands work by appending
them to `docker run`:

```bash
docker run --rm -v hammer-data:/data \
  ghcr.io/darkrock-studios/hammer-editor/server:latest generate-keyring
```
