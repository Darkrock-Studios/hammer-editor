## [3.12.0] - 2026-10-5

New features
- Reader Mode: read your story as prose, one chapter at a time, instead of through the scene editor. Open it from the Read button under the scene list, the book icon in the scene editor, or the editor menu. The scene list becomes a table of contents, and the reader has its own font size
- Export: three new options in the Export Story dialog. Number chapter titles, pick a font (Word, RTF and EPUB), and choose whether to keep blank lines between paragraphs
- Android and iOS: an OLED theme, the dark theme on a pure black background
- Desktop (Windows and Linux installs from GitHub): Hammer tells you when a newer release is out and can update itself from the project list or the About screen. macOS DMG installs are told and sent to the release page. Automatic checks can be turned off in Settings
- Text editor 3.0: Huge improvements in input and performance
- Text editor: change the text size with Ctrl/Cmd and Plus, Minus or 0, or from the format bar's overflow menu
- Text editor: Delete in the right-click menu
- Turkish translation
- Web: a fifth reader reaction on public stories, "Surprised me"

Improvements
- Syncing with unsaved work now asks Save, Discard or Cancel first, where before it silently saved every unsaved scene
- Open notes, timeline events, encyclopedia entries and the scene title, outline and notes refresh when a sync or conflict resolution changes them. An edit in progress keeps its draft
- The Backup Manager lists backups of projects that are no longer on disk, so a deleted project can be restored
- Exports no longer number chapter titles unless you turn the new option on. Word, RTF, PDF and Markdown used to number them always
- Text editor: the heading button writes real Markdown headings, and Hammer's shortcuts land on the same keys on non-QWERTY layouts
- Android: a launch splash screen on every supported version
- Translations and dependencies updated

Fixes
- Android: unsaved scene edits could be lost when the app was closed in the background. They now come back as unsaved scenes the next time the project opens   [#983]
- Export: unsaved scene edits were left out of the exported file   [#983]
- Restoring a backup from the Backup Manager made the project disappear from the project list
- Choosing Discard on close with automatic sync on still saved and uploaded the discarded scenes
- PDF export: long paragraphs ran off the bottom of the page or left large blank gaps
- Android: a crash on launch when the saved login could not be decrypted, for example after restoring the device from a backup. You are asked to sign in again
- iOS: a crash when tapping into a text editor
- Server URL, number, tag and word-list fields no longer autocorrect or capitalize, which put a space after each "." in a server URL
- Encyclopedia references no longer match a name inside a longer word with accented or non-Latin letters ("Ana" in "Anaïs")
