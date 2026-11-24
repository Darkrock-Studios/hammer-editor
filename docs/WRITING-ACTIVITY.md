# Writing Activity

Writing activity is the record of how many words a writer produced and when. It drives the
project home's Today, Last 7 days, streak, heatmap and goal progress, the Android widget, and the
words-by-author breakdown. Each device keeps its own log; how logs sync is in
[SYNCING-PROTOCOL.md](SYNCING-PROTOCOL.md#writing-activity-sync-per-device-slots).

## The rule

> Hammer counts the words you typed this session that were not already in your story. Moving text,
> pasting it back, or undoing a deletion does not count. Deleting never subtracts.

A word is what the scene word count counts (`countedWordSpans`), compared without case.

## How words are counted

`WritingSessionTracker` does the counting; the pure functions are in `WordCredit.kt`.

**Anchors.** A scene is anchored at its text when its buffer is loaded from disk (re-anchored, if
it had an anchor), when a buffer already in memory is first opened, or at its first editor save if
neither happened. Every editor save, autosave included, is compared with that anchor, never with
the previous save. So an autosave that lands mid-word, a typo fixed a minute later, or a deletion
that is undone changes nothing: only the text at each save, measured against the anchor, matters.

**What was typed.** A word diff of the anchor against the saved text gives the inserted words.
The common start and end are trimmed first. A diff matches stray common words ("the", "and")
inside a rewritten passage; changes separated by a single matched word of the new text form one
stretch, and a stretch that deletes and inserts at least six words is counted whole. When either
remaining side is longer than 2,000 words, a bag difference (surplus occurrences of each word)
stands in for the diff, which would otherwise grow with the square of the size.

**Text seen before.** The tracker keeps a hash of every four-word window, and of every line
shorter than that, from all text it has anchored or let go of since the project was opened. An
inserted word inside such a window or line was moved, pasted back or restored, not written, and
does not count. This catches moves within a scene, between scenes, and out of a deleted scene.

**Recording.** A scene's credit is its count of typed words. The epoch's total is the sum of all
scenes' credits plus credit banked from scenes that were re-anchored or deleted. A save records
only what lifts the total above its highest point so far in the epoch, so deleting typed words
and typing them again adds nothing, and nothing is ever subtracted.

**Epochs.** An epoch ends at the first editor save on a later local calendar day than it began,
or more than six hours after the last save that recorded words, much as a writing session is
sealed. Every scene is then re-anchored at its last saved text, and the total and its high point
start from zero. The tracker lives in memory with the open project, so closing and reopening a
project also starts a new epoch.

**Text from outside the editor.** When text replaces a scene's buffer from anywhere but the editor
(sync, a draft restore, a plugin or MCP write, a rename rewriting the scene, restored unsaved
edits, discarding unsaved edits), the scene is re-anchored at the new text: its credit is banked
and the new text counts as seen. The re-anchor happens under the buffer lock just before the buffer
is replaced, so an editor save is never compared with the wrong anchor. A scene the tracker does
not hold yet is left alone and anchored when it is opened. A rewrite first credits any words typed
into the buffer it rewrites. A deleted scene's credit is banked and its text stays seen.

**Order.** Every buffer change gets a revision, and each save and re-anchor carries the revision
its text was read at, so ones handled out of order are put back in order. A save older than one
already counted, or an outside text older than the newest save, changes nothing. A save read
before a re-anchor but handled after it is credited against the anchor it was typed into.

## Accepted limits

- Text pasted from another app counts as written.
- Fresh prose that repeats four consecutive words, or a whole short line, from text seen before
  loses those words.
- Writing 100 words, deleting them and writing 100 different ones in one epoch credits 100. Across
  a new epoch (a later day, a long gap or a project reopen) it credits 200.
- Duplicating text typed this epoch (copy, paste, keep both) counts twice.
- Bolding part of a word splits it into two words, which count as new.
- In a passage that is both moved and densely edited, a word within three words of an edit and of
  the move's edge can count.
- After switching accounts on one install, words written before the next sync go into the own log
  still stamped with the previous account, and that sync files them under the previous account.
- Recorded history is never corrected.

## Sessions and days

Recorded words go into the device's current `WritingSession`. A session grows while saves keep
coming on the same local calendar day, no more than six hours apart; otherwise it is sealed and a
new one begins (`WritingSessionTracker.mergeWriting`). A session's words belong to the local date
it started on.

## Whose words

With an account signed in, Today, Last 7 days, the streak, the heatmap, goal progress and words
by device count only that author's sessions, from every device they used, so on a co-authored
project each author sees their own writing. Words by author covers everyone. With no account
signed in, every log counts. A log's author is the one the server stamped on it; a log with
none is the project owner's, except this device's own log, which belongs to the signed-in account.
Cached statistics record the account they were computed for and are recalculated for another.
The `stats.activity` and `stats.sessions` operations cover every author.
