## [3.10.0] - 2026-9-20

New features
- A Wear OS watch app: dictate notes into your projects and capture story ideas from your wrist, then sync them on their own. Pair it from your phone, or sign in on the watch directly
- Add words to a project's own spelling dictionary, either from "Add to dictionary" on a flagged word in the editor or from the word list in Project Settings under Spell checking. The list syncs with the project
- Encyclopedia entry names and draft names accept the same characters project and scene names already did: punctuation like . , ! ? : ( ) & - " and non-Latin letters
- Copy Diagnostics on the About screen puts your version, system details, recent log, and newest crash report on the clipboard, so a bug report can carry its own diagnostics
- Desktop: Export Logs saves a zip wherever you choose, which works even where opening the log folder does not

Improvements
- A failed login says what actually went wrong instead of answering with a bare 401, and a self-hosted server logs which failure occurred
- Account creation reports policy problems, real conflicts, and Allowed Users rejections distinctly
- Builds record which store or channel they came from, which shows up in diagnostics
- Translations updated for German, Spanish, French, Italian, Portuguese (Brazil), and Ukrainian
- Web: the home page offers the iOS app, which now shares one App Store listing with macOS

Fixes
- Desktop (Microsoft Store): the app crashed on its first network call and could not sync at all
- Client: email and password fields no longer capitalize the first character as you type, which could create an account with a password you could not reproduce elsewhere
- Client: a network failure occurring before any request was sent showed a raw Java error instead of a real message
- Text editor: the spelling suggestions menu could stick on "Loading" when the editor re-anchored it
- Sync: clients on different versions no longer fail an entire sync over a field one side does not recognize
- Sync: a failed sync no longer ends by logging "Sync complete!"
- Web: previous and next page links on the community feed and authors pages
