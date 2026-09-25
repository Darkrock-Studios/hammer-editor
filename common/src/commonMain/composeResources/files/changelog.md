## [3.11.0] - 2026-9-24

New features
- Web: signed-in readers can leave kudos at the end of a publicly published story, picking up to four craft chips and one reaction. Kudos are anonymous: authors see per-chip totals on their story page, and a chip appears on the public page once three readers have picked it. Authors can turn kudos off for any story

Improvements
- Word counts no longer count standalone Markdown markers such as horizontal rules, heading marks, bullets and blockquote markers as words. The server now counts the same way, so both give the same number
- Android and Wear OS builds are now shrunk and obfuscated with R8
- Translations and dependencies updated

Fixes
- Text editor: numpad navigation keys and numpad Enter now work
- Text editor: keyboard (IME) edits are applied right away, and the keyboard is told about each one only once
- Text editor: spelling underlines stay on the right words while you keep typing
- Text editor: the formatting you picked for new text is kept after unrelated updates
- Text editor: fixed crashes and misplaced carets when drawing the cursor or moving it during a layout change
- Wear OS: pairing with the phone app works in release builds
