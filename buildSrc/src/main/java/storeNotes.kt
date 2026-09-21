package com.darkrockstudios.build

/** Hard character limit Google Play enforces on a release's "What's new" text. */
const val PLAY_STORE_LIMIT = 500

/** Hard character limit the Apple App Stores enforce on release notes. */
const val APPLE_STORE_LIMIT = 4000

private const val GITHUB_REPO = "https://github.com/Darkrock-Studios/hammer-editor"

private const val FOOTER_LABEL = "Full changelog:"
private const val TRUNCATION_MARK = "…"

/**
 * Audiences a changelog entry can address that the app stores are not. Store
 * listings may only describe the app itself — Apple rejects a submission whose
 * release notes cover the hosted service or self-hosting, and Google Play has an
 * equivalent relevance policy.
 */
private val NON_APP_AUDIENCES = setOf("web", "server", "operators", "devops", "backend")

/** A `[New]`-style section header: a bracketed label alone on its line. */
private val SECTION_HEADER = Regex("""^\[[^]]+]$""")

/** The longest `Label:` prefix treated as an audience label rather than prose. */
private const val MAX_LABEL_LENGTH = 20

/**
 * The GitHub release page for a release tag, where the untruncated notes live.
 *
 * Only Google Play is given this link. App Store review rejects release notes that
 * link to the project's GitHub page — it offers the app outside the App Store — so
 * the Apple stores pass `null` as the footer URL. The link shipped to iOS and macOS
 * from v3.7.0 onward and drew "invalid metadata" rejections until it was removed.
 */
fun releaseNotesUrl(tag: String): String = "$GITHUB_REPO/releases/tag/$tag"

/**
 * One changelog item: a section header, or a bullet together with the wrapped
 * continuation lines and indented sub-bullets beneath it.
 *
 * Entries — not physical lines — are the unit of filtering and truncation, so that
 * removing a bullet removes everything that only makes sense underneath it.
 */
private class Entry(val lines: MutableList<String>, val isHeader: Boolean) {
	val text: String get() = lines.joinToString("\n")

	/** The bullet or header itself, without its continuations. */
	val label: String get() = lines.first().trim()
}

private fun parseEntries(changelog: String): List<Entry> {
	val entries = mutableListOf<Entry>()
	for (line in normalizeNotes(changelog).lines()) {
		val trimmed = line.trim()
		if (trimmed.isEmpty()) continue

		// An indented line always belongs to the bullet above it, sub-bullets included.
		val indented = line.first().isWhitespace()
		val isHeader = !indented && SECTION_HEADER.matches(trimmed)
		val startsEntry =
			!indented && (isHeader || trimmed.startsWith("-") || trimmed.startsWith("["))
		val previous = entries.lastOrNull()
		if (startsEntry || previous == null || previous.isHeader) {
			entries += Entry(mutableListOf(line), isHeader)
		} else {
			previous.lines += line
		}
	}
	return entries
}

/** Renders entries back to text, restoring the blank line that precedes each section. */
private fun render(entries: List<Entry>): String = buildString {
	entries.forEachIndexed { index, entry ->
		if (index > 0) append(if (entry.isHeader) "\n\n" else "\n")
		append(entry.text)
	}
}

/** Drops section headers that no longer have any entry beneath them. */
private fun pruneEmptySections(entries: List<Entry>): List<Entry> =
	entries.filterIndexed { index, entry ->
		!entry.isHeader || entries.getOrNull(index + 1)?.isHeader == false
	}

private fun tokenize(label: String): Set<String> =
	label.split('/', ' ').mapNotNullTo(mutableSetOf()) { it.trim().lowercase().ifEmpty { null } }

/**
 * Whether `Label:` is an audience tag rather than the opening words of a sentence.
 * A tag is one word, or a few joined by `/` — so `Server:` tags a bullet, while
 * `Server address:` and `Security hardening:` are prose and keep their bullet.
 */
private fun isAudienceTag(label: String) =
	label.isNotEmpty() && label.length <= MAX_LABEL_LENGTH && !label.contains(' ')

/**
 * The audience words an entry declares. Both an opening `[Fix/Web]` tag and a
 * `Web:` label count, and a bullet may carry both: `- [New] Server: ...`.
 */
private fun audienceWords(entry: Entry): Set<String> {
	var text = entry.label.removePrefix("-").trim()
	val words = mutableSetOf<String>()

	if (text.startsWith("[")) {
		val close = text.indexOf(']')
		if (close > 0) {
			words += tokenize(text.substring(1, close))
			text = text.substring(close + 1).trim()
		}
	}

	val label = text.substringBefore(':', "")
	if (label.length < text.length && isAudienceTag(label)) {
		words += tokenize(label)
	}

	return words
}

private fun addressesNonAppAudience(entry: Entry) =
	audienceWords(entry).any { it in NON_APP_AUDIENCES }

/**
 * App-only release notes, and every entry removed to produce them.
 *
 * [dropped] holds the label of each removed entry so the release dialog can show
 * what the store listing will not say — filtering by audience tag is a heuristic,
 * and a silent removal is how an app-facing change goes missing from a listing.
 */
data class StoreNotesDerivation(val notes: String, val dropped: List<String>)

/**
 * Strips everything from [fullChangelog] that does not describe the app itself —
 * the `[Server operators]` section, and entries tagged for the web or server —
 * leaving the text that store listings are allowed to carry.
 *
 * Only an explicit audience tag removes an entry. Prose that merely mentions a
 * server stays, since a change to how the app talks to one is still a change to
 * the app; review the reported [StoreNotesDerivation.dropped] labels to catch a
 * tag that was meant literally.
 */
fun deriveStoreNotes(fullChangelog: String): StoreNotesDerivation {
	val kept = mutableListOf<Entry>()
	val dropped = mutableListOf<String>()
	var inDroppedSection = false

	for (entry in parseEntries(fullChangelog)) {
		if (entry.isHeader) {
			inDroppedSection = addressesNonAppAudience(entry)
			if (inDroppedSection) dropped += entry.label else kept += entry
			continue
		}
		if (inDroppedSection || addressesNonAppAudience(entry)) {
			dropped += entry.label
			continue
		}
		kept += entry
	}

	return StoreNotesDerivation(render(pruneEmptySections(kept)), dropped)
}

/**
 * Fits [changelog] into a store's character limit, leaving room for a footer pointing
 * at the full notes when [fullNotesUrl] is given — see [releaseNotesUrl] for why the
 * Apple stores pass null. Whole entries are kept so the text does not end
 * mid-bullet, and an entry too long for the remaining budget is skipped rather than
 * ending the notes. The `…` mark is appended only when an entry was actually left
 * out; text that fits once its blank lines are collapsed is not marked truncated.
 *
 * The one case that ends mid-sentence is a changelog whose every bullet is longer
 * than the whole budget: notes have to say something, so the first one is cut at a
 * word boundary.
 *
 * A blank changelog yields an empty string: store metadata that is only a link
 * describes nothing, and App Store review rejects it.
 */
fun formatStoreNotes(changelog: String, limit: Int, fullNotesUrl: String?): String {
	val body = normalizeNotes(changelog)
	if (body.isEmpty()) return ""

	val footer = footerFor(fullNotesUrl)
	val budget = storeNotesBudget(limit, fullNotesUrl)
	require(budget > 0) { "Limit $limit is too small to hold the changelog footer" }

	return fitStoreNotes(body, budget) + footer
}

/** How much of a store's limit is left for the notes once its footer is subtracted. */
fun storeNotesBudget(limit: Int, fullNotesUrl: String?): Int = limit - footerFor(fullNotesUrl).length

/**
 * [changelog] cut down to [limit] characters, footer excluded. Returns the body on
 * its own so the release dialog can put the fitted text back in the Google Play
 * editor for the user to improve, rather than appending a footer that the next
 * [formatStoreNotes] call would append again.
 */
fun fitStoreNotes(changelog: String, limit: Int): String {
	val body = normalizeNotes(changelog)
	if (body.isEmpty()) return ""
	require(limit > 0) { "Limit $limit leaves no room for notes" }
	if (body.length <= limit) return body

	val entries = parseEntries(body)
	val truncatedBudget = limit - TRUNCATION_MARK.length
	val kept = mutableListOf<Entry>()
	for (entry in entries) {
		val candidate = pruneEmptySections(kept + entry)
		// Headers that end up with nothing under them are pruned before the notes are
		// published, so they must not spend budget the bullets could have used.
		if (render(candidate).length > truncatedBudget) continue
		kept += entry
	}

	if (kept.none { !it.isHeader }) {
		val hardCut = body.take(truncatedBudget).trimEnd()
		val lastSpace = hardCut.lastIndexOf(' ')
		val cut = if (lastSpace > 0) hardCut.take(lastSpace) else hardCut
		return cut.trimEnd() + TRUNCATION_MARK
	}

	val fitted = render(pruneEmptySections(kept)).trimEnd()
	val mark = if (kept.size == entries.size) "" else TRUNCATION_MARK
	return fitted + mark
}

/**
 * How many characters [changelog] needs at a store, including the footer when
 * [fullNotesUrl] is given. Pass the same URL the store's [formatStoreNotes] call
 * gets — null for the Apple stores — or the "will it truncate?" comparison is off
 * by the footer's length.
 */
fun storeNotesLength(changelog: String, fullNotesUrl: String?): Int {
	val body = normalizeNotes(changelog)
	return if (body.isEmpty()) 0 else body.length + footerFor(fullNotesUrl).length
}

private fun footerFor(fullNotesUrl: String?) =
	if (fullNotesUrl == null) "" else "\n\n$FOOTER_LABEL\n$fullNotesUrl"

/**
 * CHANGELOG.md is checked out with CRLF on Windows and seeds the release dialog, so
 * the line endings are normalized up front — otherwise the fitting path (which rebuilds
 * lines with LF) and the verbatim path would disagree with the counted length.
 */
private fun normalizeNotes(changelog: String) = changelog.replace("\r\n", "\n").trim()

/**
 * The release notes each store listing carries.
 *
 * [shared] is the app-only text produced by filtering the full changelog; [play]
 * and [apple] begin as copies of it and diverge only when hand-edited in the
 * release dialog. The split exists because the two sinks have incompatible
 * budgets (500 characters with a footer link at Google Play against 4000 with no
 * link at Apple), so text written to fill one is machine-mangled at the other.
 *
 * Stores with no tab of their own take [shared]: F-Droid and Google Play read the
 * same fastlane file, so F-Droid is covered by [play].
 */
data class StoreChangelogs(
	val shared: String,
	val play: String = shared,
	val apple: String = shared,
) {
	fun notesFor(platform: Platform): String = when (platform) {
		Platform.GOOGLE_PLAY, Platform.FDROID -> play
		Platform.IOS_APP_STORE, Platform.MAC_APP_STORE -> apple
		Platform.SNAP, Platform.MS_STORE, Platform.SERVER -> shared
	}

	/** The same notes with every sink this release does not reach blanked out. */
	fun restrictedTo(platforms: Set<Platform>) = StoreChangelogs(
		shared = if (reachesFlathub(platforms)) shared else "",
		play = if (reachesPlay(platforms)) play else "",
		apple = if (reachesApple(platforms)) apple else "",
	)

	/** True when no listing would carry any text at all. */
	val isBlank: Boolean get() = shared.isBlank() && play.isBlank() && apple.isBlank()
}

/** Whether a release to [platforms] reaches the Google Play file, which F-Droid also reads. */
fun reachesPlay(platforms: Set<Platform>) =
	Platform.GOOGLE_PLAY in platforms || Platform.FDROID in platforms

/** Whether a release to [platforms] reaches either Apple listing. */
fun reachesApple(platforms: Set<Platform>) =
	Platform.IOS_APP_STORE in platforms || Platform.MAC_APP_STORE in platforms

/** Whether a release to [platforms] reaches any store listing at all. */
fun reachesAnyStore(platforms: Set<Platform>) = platforms.any { it in Platform.CLIENT_STORES }

/**
 * Whether a release to [platforms] is a Flathub release. Flathub is published by hand
 * with `publishFlathub`, outside the tag's store scope, so only a release that ships
 * every client store counts; a targeted hotfix names its stores and Flathub is not one.
 */
fun reachesFlathub(platforms: Set<Platform>) = platforms.containsAll(Platform.CLIENT_STORES)
