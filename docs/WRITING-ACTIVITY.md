# Writing Activity

Writing activity is the record of how many words a writer produced and when. It drives the
project home's Today, Last 7 days, streak, heatmap and goal progress, and the Android widget. Each
device keeps its own log; how logs sync is in
[SYNCING-PROTOCOL.md](SYNCING-PROTOCOL.md#writing-activity-sync-per-device-slots).

## The rule

> Hammer counts the words you typed this session that were not already in your story. Moving text,
> pasting it back, or undoing a deletion does not count. Deleting never subtracts.

A word is a run of non-whitespace holding a letter or digit, as the scene word count has it, with
the punctuation and markdown markers at either end stripped (combining marks such as vowel signs
are kept), compared without case. So `Dinner.`, `**dinner**` and `dinner` are the same word, and a
standalone `#` or `---` is not a word. The scene word count itself is unchanged.

## How words are counted

`WritingSessionTracker` does the counting; the pure functions are in `WordCredit.kt`.

**Anchors.** A scene is anchored at its text when its buffer is loaded from disk, when a buffer
that is already in memory is first opened, or at its first editor save if neither happened. Every
editor save, autosave included, is compared with that anchor, never with the previous save. So an
autosave that lands mid-word, a typo fixed a minute later, or a deletion that is undone changes
nothing: only the text at each save, measured against the anchor, matters.

**What was typed.** A word diff of the anchor against the saved text gives the inserted words.
The common start and end are trimmed first. A diff matches stray common words ("the", "and")
inside a rewritten passage; changes separated by a single matched word form one stretch, and a
stretch that deletes and inserts at least six words is counted whole. When either remaining side
is longer than 2,000 words, a bag difference (surplus occurrences of each word) stands in for the
diff, which would otherwise grow with the square of the size.

**Text seen before.** The tracker keeps a hash of every four-word window, and of every line
shorter than that, from all text it has anchored or let go of since the project was opened. An
inserted word inside such a window or line was moved, pasted back or restored, not written, and
does not count. This catches moves within a scene, between scenes, and out of a deleted scene.

**Recording.** A scene's credit is its count of typed words. The epoch's total is the sum of all
scenes' credits plus credit banked from scenes that were re-anchored or deleted. A save records
only what lifts the total above its highest point so far in the epoch, so deleting typed words
and typing them again adds nothing, and nothing is ever subtracted.

**Epochs.** An epoch ends where a writing session is sealed: at the first editor save on a later
local calendar day, or more than six hours after the last save that recorded words. Every scene
is then re-anchored at its last saved text, and the total and its high point start from zero. The
tracker lives in memory with the open project, so closing and reopening a project also starts a
new epoch.

**Text from outside the editor.** When sync or a draft restore replaces a scene's buffer, or the
buffer is discarded and reloaded, the scene is re-anchored at the new text: its credit is banked
and the new text counts as seen. Sync and draft content re-anchor under the buffer lock just before
the buffer is replaced, so an editor save is never compared with the wrong anchor. A scene the
tracker does not hold yet is left alone and anchored when it is opened. A deleted scene's credit
is banked and its text stays seen.

## Accepted limits

- Text pasted from another app counts as written.
- Fresh prose that repeats four consecutive words, or a whole short line, from text seen before
  loses those words.
- Writing 100 words, deleting them and writing 100 different ones in one epoch credits 100. Across
  a project reopen it credits 200.
- Duplicating text typed this epoch (copy, paste, keep both) counts twice.
- In a passage that is both moved and densely edited, a word within three words of an edit and of
  the move's edge can count.
- An inline image's alt text and link are words, as they are to the scene word count.
- Recorded history is never corrected.

## Sessions and days

Recorded words go into the device's current `WritingSession`. A session grows while saves keep
coming on the same local calendar day, no more than six hours apart; otherwise it is sealed and a
new one begins (`WritingSessionTracker.mergeWriting`). A session's words belong to the local date
it started on. The statistics add up every device's sessions.
