package com.darkrockstudios.build

/** Hard character limit Google Play enforces on a release's "What's new" text. */
const val PLAY_STORE_LIMIT = 500

/** Hard character limit the Apple App Stores enforce on release notes. */
const val APPLE_STORE_LIMIT = 4000

private const val GITHUB_REPO = "https://github.com/Darkrock-Studios/hammer-editor"

private const val FOOTER_LABEL = "Full changelog:"
private const val TRUNCATION_MARK = "…"

/** The GitHub release page for a release tag, where the untruncated notes live. */
fun releaseNotesUrl(tag: String): String = "$GITHUB_REPO/releases/tag/$tag"

/**
 * Fits [changelog] into a store's character limit, always leaving room for a footer
 * pointing at the full notes. Whole lines are kept so the text never ends mid-bullet,
 * and a line too long for the remaining budget is skipped rather than ending the notes.
 *
 * A blank changelog yields an empty string: store metadata that is only a link
 * describes nothing, and App Store review rejects it.
 */
fun formatStoreNotes(changelog: String, limit: Int, fullNotesUrl: String): String {
	val body = normalizeNotes(changelog)
	if (body.isEmpty()) return ""

	val footer = footerFor(fullNotesUrl)
	val budget = limit - footer.length
	require(budget > 0) { "Limit $limit is too small to hold the changelog footer" }

	if (body.length <= budget) return body + footer

	val truncatedBudget = budget - TRUNCATION_MARK.length
	val kept = StringBuilder()
	for (line in body.lines()) {
		val candidate = if (kept.isEmpty()) line else "$kept\n$line"
		if (candidate.length > truncatedBudget) continue
		kept.setLength(0)
		kept.append(candidate)
	}

	// Every line on its own exceeds the budget, so fall back to a word boundary
	// within the first one.
	if (kept.isEmpty()) {
		val hardCut = body.take(truncatedBudget)
		val lastSpace = hardCut.lastIndexOf(' ')
		kept.append(if (lastSpace > 0) hardCut.take(lastSpace) else hardCut)
	}

	return kept.toString().trimEnd() + TRUNCATION_MARK + footer
}

/** How many characters [changelog] needs at a store, footer included. */
fun storeNotesLength(changelog: String, fullNotesUrl: String): Int {
	val body = normalizeNotes(changelog)
	return if (body.isEmpty()) 0 else body.length + footerFor(fullNotesUrl).length
}

private fun footerFor(fullNotesUrl: String) = "\n\n$FOOTER_LABEL\n$fullNotesUrl"

/**
 * CHANGELOG.md is checked out with CRLF on Windows and seeds the release dialog, so
 * the line endings are normalized up front — otherwise the fitting path (which rebuilds
 * lines with LF) and the verbatim path would disagree with the counted length.
 */
private fun normalizeNotes(changelog: String) = changelog.replace("\r\n", "\n").trim()
