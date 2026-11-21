## [3.12.2] - 2026-11-21

- Writing statistics: autosaves landing mid-word, fixed typos, undone deletions, moved or pasted-back text and text arriving by sync or a draft restore no longer add words, and punctuation or formatting changes are not new words. "This week" is now "Last 7 days", which is what it always measured
- Sync no longer leaves synced scenes behind as unsaved edits, and skips downloading a scene that has unsaved edits
- Sync keeps a scene's missing created and edited dates missing instead of filling them per device, which made other devices conflict on scenes they had only opened
- Fix a crash when an encyclopedia entry can't be read
- Updated translations
- Server: health check endpoints for uptime monitors
