# Changelog

## [3.9.3] - 2026-8-22

Bug fixes


## [3.9.2] - 2026-8-21

- Fix MacOS Crash
- Fix Android widgets not working


## [3.9.1] - 2026-8-18

- Fix sharing scene order
- Fix PDF export crash
- Fix MacOS crash on start
- Added configurable timezone for server admins


## [3.9.0] - 2026-8-16

New features
- Export only the scenes you choose, in every export format
- Private web shares can be limited to a selection of scenes
- Encyclopedia entry names and aliases feed the spell check dictionary, so your invented names stop being flagged as misspelled. Controllable globally, per project, and per entry
- Scenes now connect to every kind of Encyclopedia entry, not just People and Places
- Drag the editor's edge to set how wide it gets on large screens
- Server: search the Allowed Users list by email

Improvements
- Logging in to a new server asks whether to merge or replace, and only asks when local work is actually at stake
- Published stories and every export now lay prose out the way the editor shows it, line for line
- A scene opens for typing immediately instead of waiting for spell check to finish
- Scene tree nesting is easier to read
- Save all moved from Ctrl+Alt+S to Ctrl+Shift+S, so AltGr layouts keep their characters
- Desktop (Linux): runs natively on Wayland instead of being forced through XWayland
- Web: the editorial review dialog's scene list uses the full height of the window
- Italian translation added, and translations updated for German, Spanish, French, Italian, Portuguese (Brazil), and Ukrainian
- Server: Argon2 password hashing no longer loads a native library, which fixes signup and login on hosts that mount the data volume noexec

Fixes
- Client: scenes in a folder holding exactly ten items could go blank and export empty
- Client: sync could fail when a folder crossed a ten-item boundary
- Client: unsaved edits survive a device rotation on Android
- Client: AltGr chords no longer close the app on Turkish, German, and Polish layouts
- Client: crash when closing a project with the scene metadata panel open
- Sync: renaming a project onto the name of one you had deleted
- Sync: project settings could be wiped when a project moved to a different server
- Sync: a local project sharing a name with a server project could leave a duplicate behind
- Server: sync could hang indefinitely on hosts with a shallow entropy pool
- Server: concurrent sync sessions could be handed colliding sync ids
- Server: API errors returned a 500 instead of falling back to English when a translation was missing
- Web: Allowed Users paging could repeat and skip entries


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


## [3.7.2] - 2026-7-27

[Improve]
- Translations updated for German, Spanish, French, Italian,
Portuguese (Brazil), and Ukrainian
- Web: faster page loads and accessibility fixes across hammer.ink
- Web: Redesign home page

[Server operators]
- Official Docker image for self-hosting the sync server


## [3.7.1] - 2026-7-23

- Fix Mac app store metadata


## [3.7.0] - 2026-7-21

[New]
- Encyclopedia: search by #tag, combined with name search
- Desktop: Ctrl+Q closes the app from the project selection window
- Crash reports now include the full stack trace in the exported logs
(Android, Desktop, and iOS)

[Improve]
- Server connections are now HTTPS-only. If you sync with a self-hosted
server over plain HTTP, it must be put behind HTTPS before upgrading.
- Web: story reader pages load faster and link previews now show a proper
title, description, and image when shared
- Web: sitemap and search engine discovery for public stories
- Web: reader stats no longer count bounces as reads
- Web: optional Terms of Service and Privacy Policy pages, with a
two-tier footer

[Fix]
- Web: error when saving your bio
- Client: Text editor crash

[Server operators]
- Optional Terms of Service acceptance gate on account creation
- Whitelist invites can be given an expiry date
- Configurable disk cache directory
- Configurable extra navigation links


## [3.6.1] - 2026-7-12

- [New] Re-added translations
- [New] iOS can now export logs
- [Fix] Encyclopedia image selection
- [Fix] Focus mode UI padding on Desktop


## [3.6.0] - 2026-7-7

[New]
- Ideas: capture ideas for new stories
- Project Tags: tag your projects and filter the project list by tag
- "Move To" dialog for moving a scene to an exact spot
[Improve]
- Writing activity reliability improvements
- More reliable sync with automatic self-healing when data gets out of step
[Fix]
- Crash when importing an image on iOS
- Crash on some Linux systems
- Notes could revert to an older version during sync


## [3.5.3] - 2026-6-30

- [Fix] Several sync protocol bugs
- [Fix] Spellcheck not respecting disable setting


## [3.5.2] - 2026-6-29

- [Fix] More hang on start when upgrading older clients
- [Fix] iOS crash on start
- [Fix] Restore hover values in Project home dashboard


## [3.5.1] - 2026-6-27

- [Fix] Hang on start when upgrading older clients
- [Fix] Crash when deleting a scene


## [3.5.0] - 2026-6-27

- [New] Import existing stories (from Markdown or RTF) from the new-project flow
- [New] New export format: RTF
- [New] Draft compare: Paragraph move highlighting
- [Fix] Editor undo/redo getting broken by formatting
- [Fix] Timeline reordering events snapping back into place
- [Fix] Breaking change: Sync API URLs now only use project IDs
- Security hardening: sync auth tokens encrypted at rest, file write sandboxing, max image size enforced


## [3.4.2] - 2026-6-23

- [Fix] Server security hardening


## [3.4.1] - 2026-6-23

- [Fix] Server session token hmac fix


## [3.4.0] - 2026-6-22

- [New] Shortcuts: Ctrl+W (close project), Ctrl+Q (quit), Ctrl+Alt+S (save all)
- [Fix] Markdown import maps heading levels to scene/group hierarchy
- [Fix] Warn before discarding unsaved timeline edits on close
- [Fix] Backups missing from Manage Backups
- [Fix] Spell-check crash
- [Fix] scene-tree flicker and performance
- [Fix] Allow Windows reserved names in scene/group titles
- [New] Server: Improved cryptography key management
- [Fix] Server: Reworked project URLs to fix 404s


## [3.3.1] - 2026-6-16

[New/Client] Added more text editor keyboard shortcuts (bold, italic and strikethrough, and more)
[New/Server] Active Users metric on the monitoring dashboard
[New/Server] Per-story reader counter and unique active-users metrics
[Improve/Server] Monitoring dashboard distinguishes client-fault from server-fault errors
[Fix/Server] Error-rate metric now includes exception-driven errors
[Fix/Server] Dashboard time buckets now roll over correctly
[Fix/Web] Editorial review character escaping
[Fix/Web] Mobile nav drawer rendering beneath page content


## [3.3.0] - 2026-6-13

- [New/Web] New Feature: Request editor review
- [New/Client] Added .docx export format
- [New/Sync] Faster syncing with many projects
- [Fix] Improved PDF export
- [Fix] Many many iOS bugs
- [Fix] Timeline drag-to-reorder
- [New/Client] Removed Timeline sorting
- [Fix] Rich text editor bugs


## [3.2.1] - 2026-6-10

- [Fix] several iOS crashes
- [Fix] Sync error where scenes always conflicted with them selves
- [Fix] Sync server now allows a client to reclaim it's token
- [Improve] Desktop/iOS can now select their spell check language


## [3.2.0] - 2026-6-8

- Added PDF export option
- Re-enabled public-storage of projects on F-Droid builds
- Improved text input: Dead Key keyboards now supported on desktop
- Server admin panel now includes a monitoring dashboard
- Fixed notifications/toasts not appearing on Android
- Fixed a crash when opening projects with older saved data
- Refactored Domain layer to improve testability
- Performance improvements for the Scene Tree


## [3.1.3] - 2026-6-4

- EPUB export! Now with a Table of Contents and custom file naming
- Visualdiffs when comparing drafts and merging sync conflicts
- Distraction-free editing: phones hide everything but the editor while typing
- New desktop splash screen
- Timeline Z-A sort option
- More reliable sync error handling
- Faster Linux AppImage delta updates
- Fix: Spellcheck disabling
- Fix: Sync re-ID error
- Updated translations
- Server: Migrate DB to Postgres
- Server: Optional analytics


## [3.1.2] - 2026-6-3

- EPUB export! Now with a Table of Contents and custom file naming
- Visualdiffs when comparing drafts and merging sync conflicts
- Distraction-free editing: phones hide everything but the editor while typing
- New desktop splash screen
- Timeline Z-A sort option
- More reliable sync error handling
- Faster Linux AppImage delta updates
- Fix: Spellcheck disabling
- Fix: Sync re-ID error
- Updated translations
- Server: Migrate DB to Postgres
- Server: Optional analytics


## [3.1.1] - 2026-6-2

- EPUB export! Now with a Table of Contents and custom file naming
- Visualdiffs when comparing drafts and merging sync conflicts
- Distraction-free editing: phones hide everything but the editor while typing
- New desktop splash screen
- Timeline Z-A sort option
- More reliable sync error handling
- Faster Linux AppImage delta updates
- Fix: Spellcheck disabling
- Crash fixes
- Updated translations
- Server: Migrate DB to Postgres
- Server: Optional analytics


## [3.1.0] - 2026-6-1

- EPUB export! Now with a Table of Contents and custom file naming
- Visualdiffs when comparing drafts and merging sync conflicts
- Distraction-free editing: phones hide everything but the editor while typing
- New desktop splash screen
- Timeline Z-A sort option
- More reliable sync error handling
- Faster Linux AppImage delta updates
- Fix: Spellcheck disabling
- Crash fixes
- Updated translations
- Server: Migrate DB to Postgres
- Server: Optional analytics


## [3.0.3] - 2026-5-17

- Complete UI redesign!
- Global Search: Ctrl + Shift + F
- Tags on everything! Help organize your notes, scenes, and more
- Connections: Scenes now understand what Encylopedia entries are related to them
- Writing Activity Tracker: Keep motivate to keep writing!
- New Android widgets
- Rich Text support in Notes, Encylopedia, and Timeline
- Rewritten android text input
- Made stats interactive
- Fixed short cuts
- Microsoft store fixes
- Crash fixes


## [3.0.2] - 2026-5-15

- Complete UI redesign!
- Global Search: Ctrl + Shift + F
- Tags on everything! Help organize your notes, scenes, and more
- Connections: Scenes now understand what Encylopedia entries are related to them
- Writing Activity Tracker: Keep motivate to keep writing!
- New Android widgets
- Rich Text support in Notes, Encylopedia, and Timeline
- Rewritten android text input
- Made stats interactive
- Fixed short cuts
- Microsoft store fixes


## [3.0.1] - 2026-5-14

- Complete UI redesign!
- Global Search: Ctrl + Shift + F
- Tags on everything! Help organize your notes, scenes, and more
- Connections: Scenes now understand what Encylopedia entries are related to them
- Writing Activity Tracker: Keep motivate to keep writing!
- New Android widgets
- Rich Text support in Notes, Encylopedia, and Timeline
- Rewritten android text input
- Made stats interactive
- Fixed short cuts


## [3.0.0] - 2026-5-14

- Complete UI redesign!
- Global Search: Ctrl + Shift + F
- Tags on everything! Help organize your notes, scenes, and more
- Connections: Scenes now understand what Encylopedia entries are related to them
- Writing Activity Tracker: Keep motivate to keep writing!
- New Android widgets
- Rich Text support in Notes, Encylopedia, and Timeline
- Rewritten android text input


## [2.2.0] - 2026-4-26

- Global Search across your entire story, with filters by entity type
- Text Editor improvements
- Import Story from file
- Export Options dialog with configurable export settings
- Focus Mode is now a fullscreen overlay, giving more writing space
- More project statistics
- Android: logs are now saved to disk and shareable as a zip
- Fixed encyclopedia entries not showing tags
- Fixed long project names hiding the menu
- Smoother dialog animations


## [2.1.4] - 2026-2-7

- Fix more Android input issues


## [2.1.3] - 2026-1-24

- Working on Flathub release


## [2.1.2] - 2026-1-17

- Working on Flathub release


## [2.1.1] - 2026-1-15

- Flathub release


## [2.1.0] - 2026-1-13

- Implemented Scene Archiving, never delete a scene again if you don't want!
- Improved mobile touch text selection
- Fixed several crashes
- Improved Client side error messages from networking
- Improved Server Setup error handling
- Sever Admin UI improvements
- Added confirmation dialog when discarding scene buffer
- Syncing protocol improvments: Self Healing on scheme changes


## [2.0.1] - 2026-1-11

Hammer is out of beta!

- Server improvements:
  + Added Community features
  + Added About Page
  + Added Password reset system
  + Security hardening
  + Improved SSL cert handling
- Client: Added sync server info dialog
- Build improvements


## [2.0.0] - 2026-1-10

Hammer is out of beta!

- Server improvements:
  + Added Community features
  + Added About Page
  + Added Password reset system
  + Security hardening
  + Improved SSL cert handling
- Client: Added sync server info dialog
- Build improvements


## [1.14.0] - 2026-1-6

New Features
- Find & Replace in text editor
- Search & Sort for Notes
- Export Backups

Improvements
- New icons throughout the app
- Improved create dialogs for Encyclopedia, Notes, and Events
- Better section headers UI
- Improved sync cancellation with confirmation prompt

Fixes
- Desktop menu fixes
- Removed unsupported languages


## [1.13.2] - 2026-1-3

- The backup system is now implemented on all Clients, not just Desktop
- Added Backup Managment UI to delete or restore from backups
- Improved the Project page for authors on the web
- Minor Text Editor improvements in the Scene Editor
- UI Polish: bettery dialogs, performance improvements, fix snackbars
- Focus Mode:
  + Better use of screen space on small screens
  + Android clients now enable Do Not Disturb when in focus mode
- Fix race condition in FocusMode
- Android keyboard fixes


## [1.13.1] - 2025-12-31

- The backup system is now implemented on all Clients, not just Desktop
- Added Backup Managment UI to delete or restore from backups
- Improved the Project page for authors on the web
- Minor Text Editor improvements in the Scene Editor
- UI Polish: bettery dialogs, performance improvements, fix snackbars
- Focus Mode Improvements:
  + Better use of screen space on small screens
  + Android clients now enable Do Not Disturb when in focus mode
- Fix race condition in FocusMode


## [1.13.0] - 2025-12-30

- The backup system is now implemented on all Clients, not just Desktop
- Added Backup Managment UI to delete or restore from backups
- Improved the Project page for authors on the web
- Minor Text Editor improvements in the Scene Editor
- UI Polish: bettery dialogs, performance improvements, fix snackbars
- Focus Mode Improvements:
  + Better use of screen space on small screens
  + Android clients now enable Do Not Disturb when in focus mode


## [1.12.4] - 2025-12-27

- Added story sharing on the web: publish stories publicly with author pages or share privately
- Redesigned server web UI with user management admin page
- Improved sync error handling for projects with missing IDs
- Fixed many bugs with the Rich Text Editor
- UI improvements: better dialogs, encyclopedia performance, edge-to-edge Android
- Hotfix for syncing issue
- New distributions!


## [1.12.3] - 2025-12-26

- Added story sharing on the web: publish stories publicly with author pages or share privately
- Redesigned server web UI with user management admin page
- Improved sync error handling for projects with missing IDs
- Fixed many bugs with the Rich Text Editor
- UI improvements: better dialogs, encyclopedia performance, edge-to-edge Android
- Hotfix for syncing issue
- New distributions!


## [1.12.2] - 2025-12-26

- Added story sharing on the web: publish stories publicly with author pages or share privately
- Redesigned server web UI with user management admin page
- Improved sync error handling for projects with missing IDs
- Fixed many bugs with the Rich Text Editor
- UI improvements: better dialogs, encyclopedia performance, edge-to-edge Android
- Hotfix for syncing issue
- New distributions!


## [1.12.1] - 2025-12-25

- Added story sharing on the web: publish stories publicly with author pages or share privately
- Redesigned server web UI with user management admin page
- Improved sync error handling for projects with missing IDs
- Fixed many bugs with the Rich Text Editor
- UI improvements: better dialogs, encyclopedia performance, edge-to-edge Android
- Hotfix for syncing issue
- Hotfix client crash


## [1.12.0] - 2025-12-24

  - Added story sharing on the web: publish stories publicly with author pages or share privately
  - Redesigned server web UI with user management admin page
  - Improved sync error handling for projects with missing IDs
  - Fixed many bugs with the Rich Text Editor
  - UI improvements: better dialogs, encyclopedia performance, edge-to-edge Android


## [1.11.1] - 2025-12-21

Client:
- Huge ammount of infrastructure updates
- UI overhaul! Lots of animations and polish on all parts of the UI
- Predictive back and Edge to Edge support on Android
- Drafts: Set the name of the draft when you create it
- TextEditor bug fixes!
- Spell Checker replaced with the system spell checker

Server:
- Complete web-frontend rewrite
  + Regular User's can now log in and view their stories
- Security improvements
- Encryption performance improvements
- Hotfix


## [1.11.0] - 2025-12-20

Client:
- Huge ammount of infrastructure updates
- UI overhaul! Lots of animations and polish on all parts of the UI
- Predictive back and Edge to Edge support on Android
- Drafts: Set the name of the draft when you create it
- TextEditor bug fixes!
- Spell Checker replaced with the system spell checker
Server:
- Complete web-frontend rewrite
  + Regular User's can now log in and view their stories
- Security improvements
- Encryption performance improvements


## [1.10.2] - 2025-6-5

- Updated TextEditor to fix a few bugs


## [1.10.0] - 2025-4-2

- Text Editor 2.0!!
   > This is the begining, it has Experimental Spell Checking. Many more features to come enabled by this.
- Context menu in Scene List can now rename Scenes and Groups
- Added Project Settings menu


## [1.9.2] - 2024-11-2

- Fixed Android minify crashes, the binary is now 60% smaller!


## [1.9.1] - 2024-11-2

- Disable Android obfuscation


## [1.9.0] - 2024-11-1

- Major refactor of the client syncing code
Dozens of bugs squashed and thousands of lines of test code written
- Major UI library update should improve performance


## [1.8.1] - 2024-10-8

- Removed ability to store files in public storage for Android


## [1.8.0] - 2024-10-7

- Major server refactor: All clients must re-authenticate
- Server: User project data is now encrypted at rest
- Feature: Projects can now be renamed
- Android: Project data can be moved to public storage


## [1.7.2] - 2024-8-14

- Fixed Scene syncing for scenes with metadata (outlines and notes)


## [1.7.1] - 2024-6-18

- Tons of under the hood library upgrades
- Should see some what better performance
- Fixed critcal syncing bug on the server side


## [1.7.0] - 2024-1-31

- New Feature: Focus Mode, edit a scene with less UI clutter
- Improved Encyclopedia hashtag search, allow partial tag searching
- Fix dropdown menu not showing selected item
- Fixed Scene editor not closing when scene was deleted
- Fix scene count in Project Home
- Minor UI polish
- DevOps: Greatly improved release process


## [1.6.0] - 2024-01-19

- Added Scene Metadata
- Added Story Outline overview: View your story in outline mode
- Added Font size control in Scene editor
- Encyclopedia Search UI updates
- Android folding device support
- UI polish and tweaks
- Loads of bug and crash fixes

--

_initial release._

