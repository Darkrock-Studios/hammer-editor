## [3.8.2] - 2026-8-11

New features
- Web: public sign-up page, so an allowed user can create an account without installing the app
- Server: the Allowed Users list is now always enforced; the old whitelist toggle is gone

Improvements
- Server: web sign-ins are now recorded in the security audit trail, alongside app sign-ins
- Server: admin instance messages render as Markdown, so links, emphasis, and lists work in them
- About: Dark Rock Studios attribution moved into its own "Studio" section with a full-screen Cairn overlay
- Desktop: dropped the JetBrains Runtime requirement; builds run on stock Temurin OpenJDK now
- Translations updated for German, Spanish, French, Italian, Portuguese (Brazil), and Ukrainian

Fixes
- Desktop (macOS): fixed a crash on launch
- Desktop (Linux/Snap): fixed a crash on launch
