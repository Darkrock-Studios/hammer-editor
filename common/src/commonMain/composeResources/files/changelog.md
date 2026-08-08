## [3.8.1] - 2026-8-8

Improvements
- Desktop window decoration migrated to the Nucleus TAO backend
- Server: trustProxyForwarding option reads real client IPs from X-Forwarded-* headers when running behind a reverse proxy, so the login rate limiter, login audit trail, and story reader counts reflect actual clients instead of the proxy
- Translations updated for German, Spanish, French, Italian, Portuguese (Brazil), and Ukrainian
- Rendered stories now use real book typography: indented lists, a centered scene-break ornament, tinted blockquotes, and deliberate blank-line spacing between paragraphs

Fixes
- Client: text editor no longer drops block structure (horizontal rules, images, code fences) when a scene loads
- Client: font size changes no longer flatten list and blockquote indents
- Client: typed text after a size change could lose the paragraph's body style
- Client: F3 and Ctrl+Alt+S sync shortcuts work from anywhere in the project window
- Client: tag suggestion chips could add the typed prefix instead of the selected tag
- Sync: projects with a stale cached server id could get stuck failing "410 Gone" instead of recreating
- Web: ~~strikethrough~~ rendered as literal tildes instead of struck-through text
