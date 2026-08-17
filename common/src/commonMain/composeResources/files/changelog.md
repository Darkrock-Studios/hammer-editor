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
