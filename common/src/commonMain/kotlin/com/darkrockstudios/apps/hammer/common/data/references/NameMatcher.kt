package com.darkrockstudios.apps.hammer.common.data.references

import kotlin.concurrent.Volatile

enum class MatchKind {
	EXACT,
	ALL_CAPS,
	PLURAL,
}

/**
 * [matchedText] is the form as declared on the entry; [range] covers the text actually matched,
 * which differs from it for [MatchKind.ALL_CAPS] and [MatchKind.PLURAL].
 */
data class MatchHit(
	val entryId: Int,
	val matchedText: String,
	val range: IntRange,
	val kind: MatchKind,
)

data class MatchableEntry(
	val entryId: Int,
	val names: List<String>,
)

interface NameMatcher {
	fun findMatches(
		text: String,
		entries: List<MatchableEntry>,
		includeAllCaps: Boolean = false,
		includePlurals: Boolean = false,
	): List<MatchHit>
}

/**
 * Matches every form of every entry in one pass, longest form first, so where forms overlap
 * ("Martha Tallow" and "Tallow") the text is attributed only to the longest. A form declared by
 * several entries hits each of them.
 */
class WholeWordCaseSensitiveMatcher : NameMatcher {

	private class Owner(val entryId: Int, val form: String, val kind: MatchKind)

	private class Compiled(
		val entries: List<MatchableEntry>,
		val includeAllCaps: Boolean,
		val includePlurals: Boolean,
		val regex: Regex?,
		val owners: Map<String, List<Owner>>,
	)

	@Volatile
	private var cache: Compiled? = null

	override fun findMatches(
		text: String,
		entries: List<MatchableEntry>,
		includeAllCaps: Boolean,
		includePlurals: Boolean,
	): List<MatchHit> {
		if (text.isEmpty() || entries.isEmpty()) return emptyList()

		val compiled = compile(entries, includeAllCaps, includePlurals)
		val regex = compiled.regex ?: return emptyList()

		val hits = mutableListOf<MatchHit>()
		for (match in regex.findAll(text)) {
			val owners = compiled.owners[match.value] ?: continue
			for (owner in owners) {
				hits.add(MatchHit(owner.entryId, owner.form, match.range, owner.kind))
			}
		}
		return hits
	}

	private fun compile(entries: List<MatchableEntry>, includeAllCaps: Boolean, includePlurals: Boolean): Compiled {
		cache?.let {
			if (it.entries === entries && it.includeAllCaps == includeAllCaps && it.includePlurals == includePlurals) return it
		}

		val owners = mutableMapOf<String, MutableList<Owner>>()
		fun add(variant: String, owner: Owner) {
			owners.getOrPut(variant) { mutableListOf() }.add(owner)
		}
		for (entry in entries) {
			for (name in entry.names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
				add(name, Owner(entry.entryId, name, MatchKind.EXACT))
				if (includeAllCaps) {
					val caps = name.uppercase()
					if (caps != name) add(caps, Owner(entry.entryId, name, MatchKind.ALL_CAPS))
				}
				if (includePlurals && name.last().isLetter()) {
					for (suffix in PLURAL_SUFFIXES) add(name + suffix, Owner(entry.entryId, name, MatchKind.PLURAL))
				}
			}
		}
		// Where one text is several kinds of variant (an entry named "BOB" and the caps of "Bob"),
		// only its most literal owners count.
		val resolved = owners.mapValues { (_, list) ->
			val best = list.minOf { it.kind }
			list.filter { it.kind == best }.distinctBy { it.entryId }
		}

		val regex = if (resolved.isEmpty()) {
			null
		} else {
			val alternation = resolved.keys
				.sortedByDescending { it.length }
				.joinToString("|") { Regex.escape(it) }
			Regex("$NO_WORD_BEFORE(?:$alternation)$NO_WORD_AFTER")
		}
		return Compiled(entries, includeAllCaps, includePlurals, regex, resolved).also { cache = it }
	}

	companion object {
		private val PLURAL_SUFFIXES = listOf("s", "es")

		// Reject only adjacent letters/combining marks/digits/underscores in any script;
		// punctuation and whitespace are fine on either side. This is more permissive than `\b`,
		// which would fail when the name itself starts or ends with a non-word character
		// (e.g. an alias "Mr. Smith").
		private const val WORD_CHAR = """[\p{L}\p{M}\p{N}_]"""
		private const val NO_WORD_BEFORE = "(?<!$WORD_CHAR)"
		private const val NO_WORD_AFTER = "(?!$WORD_CHAR)"
	}
}
