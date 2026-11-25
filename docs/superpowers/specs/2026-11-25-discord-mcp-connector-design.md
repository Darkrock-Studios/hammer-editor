# Discord MCP connector: design

Status: draft for approval · 2026-11-25

## Intent

People report bugs and request features in the Hammer Discord. Those reports
get lost in chat. The owner wants any Claude session (claude.ai web/desktop/
mobile, Claude Code local or cloud) to be able to read the relevant Discord
channels, so Claude can turn reports into GitHub issues on
`Darkrock-Studios/hammer-editor`.

It must not depend on one particular Claude Code cloud environment. That rules
out "save a bot token as a secret on this environment and curl Discord".

## How it works

```
Discord server ──REST + bot token──► Discord MCP server (self-hosted, Docker)
                                            ▲
                                    HTTPS + OAuth (MCP Streamable HTTP)
                                            │
                              claude.ai custom connector
                                            │
                                 any Claude session ──GitHub tools──► issues
```

1. **Discord side.** A Discord application with a bot user, created by the owner.
   It has the Message Content privileged intent on, and is invited only to the
   channels that matter, with these permissions: View Channels, Read Message
   History, Add Reactions, Send Messages, Send Messages in Threads. The bot
   never holds a gateway connection for reading. It appears offline, which is
   expected.
2. **MCP server.** A small, stateless-for-Discord Kotlin/Ktor service. It
   exposes MCP tools over Streamable HTTP. Each tool call maps to one or a few
   Discord REST calls and returns trimmed JSON. The bot token lives only in
   this service's environment.
3. **claude.ai.** The owner adds the server's public URL as a custom connector
   once. Every session on the account then has the tools.

Day-to-day flow, owned by Claude in the session and not by the server: the
owner asks "triage #bug-reports since Monday". Claude reads the messages and
threads, skips anything already marked triaged, searches GitHub for
duplicates, and shows draft issues. **The owner approves.** Claude files the
issues with the Discord message link in each body, marks the source message
triaged, and optionally replies in Discord with the issue link.

## Goals

- Any Claude session on the owner's account can read the Hammer Discord
  channels the bot is allowed into, including forum posts and threads.
- Reports that have already been triaged are recognisable without the server
  keeping its own database (see "Triage state").
- Claude can close the loop in Discord: a reaction, plus an optional reply
  with the issue link.
- The service is cheap to run: one small container on the owner's homelab.

## Non-goals (for now)

- Real-time behaviour: no gateway listener, no `/report` slash command, no
  instant auto-filing. This can be added later as a separate feature.
- Filing GitHub issues from the server. Claude does that with its own GitHub
  tools, so the server never holds GitHub write credentials.
- More than one Discord server, or general Discord administration.
- Filing issues without the owner's approval.

## Anti-goals (failure even if it "works")

- Anyone who finds the URL can read the Discord server through it. Auth is
  mandatory.
- The bot can moderate, delete, kick, or edit others' messages. Permissions
  stay minimal.
- The bot token ends up in the repo, in a Claude session, or in logs.
- The bot spams the community. Replies only happen when the owner asks for
  them in a session.

## Approaches considered

| Approach | Trade-off | Outcome |
|---|---|---|
| **Hosted MCP server + claude.ai custom connector** | Needs an always-on box, public HTTPS and OAuth; works in every Claude surface | **Chosen** |
| Local stdio MCP server in repo `.mcp.json` | No hosting, but Claude Code only, and every machine/environment needs the token | Rejected: not "any session" |
| GitHub Action on a schedule that calls Claude | Fully automatic, but no interactive reading from other sessions and no approval step | Rejected: wrong shape |

## Decided

- **Hosting:** self-hosted on one of the owner's machines (Charon or Hydra) as a
  Docker container. *Which machine, and how it is exposed publicly, is still
  open (see below).*
- **Language/stack:** Kotlin + Ktor + the official MCP Kotlin SDK
  (`io.modelcontextprotocol:kotlin-sdk`). *Claude's call:* it matches the repo's
  `server` module (Kotlin 2.4, Ktor 3.6) and the owner's day job.

## Claude's calls (the owner can override)

- **Code location:** `tools/discord-mcp/` in this repo, as a **standalone
  Gradle build**. It is not added to `settings.gradle.kts`, so the F-Droid,
  app and server builds are untouched. It reuses `gradle/libs.versions.toml`
  through `versionCatalogs { from(files(...)) }` so Kotlin/Ktor versions stay
  in step. It ships with its own `Dockerfile` and a `docker-compose.yml`
  snippet.
- **Tools exposed** (names are indicative):
  - `list_channels()`: text, announcement and forum channels the bot can see,
    with category and topic.
  - `read_messages(channel_id, since?, before?, limit?)`: chronological
    messages with author display name, timestamp, content, attachment URLs,
    reply-to, jump link, and a `triaged` flag. Pages internally up to a sane
    cap (for example 500 messages).
  - `list_threads(channel_id, since?)`: active and archived public threads
    (forum posts) with title, tags, starter message and `triaged` flag.
  - `get_message(channel_id, message_id)`
  - `mark_triaged(channel_id, message_id)`: adds the triage reaction.
  - `reply(channel_id, message_id, content)`: write. It is a separate tool so
    the owner can set it to "ask every time" in claude.ai connector
    permissions.
- **Triage state:** stored in Discord, not in the server. A bot reaction (🎫)
  marks a triaged message, and the GitHub issue body contains the Discord jump
  link, so searching GitHub for the link is the second dedupe check.
- **Channel allowlist:** the service also enforces a `DISCORD_CHANNEL_ALLOWLIST`
  env var, on top of Discord permissions. This gives defence in depth if the
  bot is later added to more channels.
- **Auth:** the server is its own minimal OAuth 2.1 authorization server, as
  the MCP spec expects: protected-resource and auth-server metadata, dynamic
  client registration, authorization code + PKCE, refresh tokens. Login is
  delegated to **GitHub OAuth** and allowed only for an allowlist of GitHub
  user IDs (the owner). Tokens are persisted in a small SQLite file on a volume
  so restarts don't force re-login. *If the owner already has an identity layer
  in front of their homelab, that may replace this (see below).*
- **Discord API hygiene:** honour `429`/`retry_after` and per-route rate-limit
  buckets. Set a proper `User-Agent: DiscordBot (url, version)`. Never log
  message content or tokens at info level.
- **Config:** env vars only: `DISCORD_BOT_TOKEN`, `DISCORD_GUILD_ID`,
  `DISCORD_CHANNEL_ALLOWLIST`, `PUBLIC_BASE_URL`, `GITHUB_OAUTH_CLIENT_ID`,
  `GITHUB_OAUTH_CLIENT_SECRET`, `ALLOWED_GITHUB_USER_IDS`, `DATA_DIR`.
- **What counts as a report:** support both. A forum post (thread) is one
  report. Its starter message carries the 🎫, and its ID equals the thread
  ID. Forum tags are returned and can be filtered on. A top-level message in a
  text channel is one report, and replies or threads hanging off it are
  returned as context. Which channels go in the allowlist is deploy-time
  config.
- **Triage marker:** only the bot's own 🎫 counts (`me: true` on the
  reaction), so a user's 🎫 can't hide a report. Replies set
  `allowed_mentions` to none.
- **Claude surfaces / redirect URIs:** dynamic client registration accepts
  claude.ai's connector callback, plus loopback (`http://localhost:*` /
  `127.0.0.1`) redirects so Claude Code can also connect directly. Loopback is
  acceptable because every login still has to pass the GitHub user-ID
  allowlist. Connectors added on claude.ai already show up in Claude Code
  sessions signed in to the same account. The README covers personal accounts
  and Team/Enterprise orgs, where an admin adds the connector.
- **Transport behind proxies:** install Ktor `ForwardedHeaders`, derive every
  issuer, resource and callback URL from `PUBLIC_BASE_URL`, and document
  disabling response buffering for the SSE parts of Streamable HTTP.
- **CI and image:** a path-filtered workflow (`tools/discord-mcp/**`,
  `gradle/libs.versions.toml`) builds and tests the standalone build. This way
  Renovate catalog bumps that break it are caught. On `develop` it publishes
  `ghcr.io/darkrock-studios/hammer-editor/discord-mcp` for
  `linux/amd64,linux/arm64`, mirroring `publish-docker.yml`. The Docker build
  context is the repo root because of the shared catalog.
- **Testing:** unit tests against a mocked Discord API (Ktor `MockEngine`), an
  MCP round-trip test using the SDK client, and an OAuth flow test.
- **Docs:** `tools/discord-mcp/README.md` with the Discord Developer Portal
  steps, Docker run, and claude.ai connector setup.

## Open: the owner decides

- **Which machine (Charon or Hydra), and how it is reachable from the internet
  over HTTPS** (Cloudflare Tunnel, reverse proxy with a domain, Tailscale
  Funnel…). This decides packaging, the public URL and TLS.
- **Whether an existing auth layer** (the one in front of the owner's Home
  Assistant MCP server, for example) should be reused instead of the built-in
  GitHub-login OAuth.
- Note for the two questions above: a plain SSO proxy that serves a browser
  login page (Cloudflare Access, Authelia and similar) **breaks** claude.ai.
  An existing layer is only reusable if it speaks MCP-style OAuth: protected-
  resource metadata, DCR or a pre-registered client, and a `WWW-Authenticate`
  challenge. A Tailscale-only (tailnet) setup can't be reached from Anthropic's
  cloud. Cloudflare bot protection may need a rule so it doesn't block
  Anthropic's servers.

## Left to the builder

- Exact MCP Kotlin SDK version and the Ktor wiring for Streamable HTTP.
- JSON shapes of tool results, as long as they stay compact.
- Whether a one-time gateway IDENTIFY is needed before the bot's first REST
  message send; handle it if Discord requires it.
- Container base image and JVM flags.
