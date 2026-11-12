# Docker files for the Hammer sync server

| File | Purpose |
|------|---------|
| [`Dockerfile`](Dockerfile) | Runtime image; packages the pre-built server distribution |
| [`docker-compose.yml`](docker-compose.yml) | Turnkey setup with a data volume |
| [`config.example.toml`](config.example.toml) | Starting point for `config.toml` |
| [`download-ngrams.sh`](download-ngrams.sh) | Fetches LanguageTool n-gram data for the optional `languagetool` service |

**Documentation lives in
[../docs/HOW-TO-RUN-A-SERVER-DOCKER.md](../docs/HOW-TO-RUN-A-SERVER-DOCKER.md)** —
quick start, networking and TLS, external PostgreSQL, backups, and building the
image yourself.

```bash
cd docker
docker compose up -d
```
