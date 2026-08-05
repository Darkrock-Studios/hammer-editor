## [3.8.0] - 2026-8-4

https://hammer.ink/blog/news/hammer-3-8-0

### New features

- Set the language a project is written in, and have spell check follow it
- Self-service account deletion from the web dashboard, with an admin restore window
- Import and export documents as HTML, alongside Markdown
- "What's New" release notes shown in-app with no network request, and reopenable from About
- Import preview warns when a scene comes in unusually large, so a failed chapter split doesn't quietly collapse a manuscript into one scene

### Improvements

- Text editor: faster typing and relayout, word-wise undo with a much deeper history, and rich copy and paste that preserves formatting between apps
- Text editor: platform-correct key bindings on macOS, and AltGr chords type their character instead of firing shortcuts
- Large Markdown imports are dramatically faster: 1600 scenes went from 37 seconds to about 3
- Search matches the rendered prose, resolving Markdown escapes and formatting
- Tag search matches accented and non-latin characters, and searches entry names as well as bodies
- Scene drag and drop stays accurate while the list autoscrolls
- Chapter detection on Markdown import understands Setext-style and bold titles
- Visible scrollbars on scrolling screens, and scroll-away footers float over their lists
- Translations updated for German, Spanish, French, Italian, Portuguese (Brazil), and Ukrainian
- Server: the setup page can create the initial admin account
- Server: a self-hosted server not serving HTTPS now reports a clear error instead of "Network error"
- Server: smaller Docker image
- Web: error responses show a toast instead of failing silently

### Fixes

- Client: importing a very large story no longer freezes the app
- Client: scene drag could grab the wrong row
- Client: literal `**` and `_` left around emphasis imported from RTF
- Client: sync server URLs are normalized consistently when saved
- Client: a number of text editor crashes around undo, paste, and joined paragraphs
- Web: htmx requests denied access landed on a blank page instead of being redirected
- Web: broken download link on the home page
