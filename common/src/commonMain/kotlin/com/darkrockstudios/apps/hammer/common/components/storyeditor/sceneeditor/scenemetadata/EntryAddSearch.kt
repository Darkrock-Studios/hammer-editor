package com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef

/**
 * Pre-loaded searchable representation of an encyclopedia entry: its [EntryDef]
 * plus its searchable terms (name + aliases) lower-cased once for cheap
 * case-insensitive comparison.
 *
 * Built up-front in the component on each `entryListFlow` emission so the
 * search-as-you-type field can filter against an in-memory list without
 * touching disk per keystroke.
 */
internal data class SearchableEntry(
	val entryDef: EntryDef,
	val lowerCaseSearchTerms: List<String>,
)

/**
 * Pure filter for the manual "add reference" search box. Extracted to a
 * top-level function so it can be unit-tested directly without spinning up
 * the Decompose component.
 *
 * Behavior:
 * - Empty / blank query -> empty result.
 * - Match: case-insensitive substring against name and any alias.
 * - Already-confirmed entries are excluded (they're already chips above the
 *   search field; surfacing them again is noise).
 * - Already-dismissed entries are included, with `isDismissed = true` so the
 *   UI can hint that picking will move them back to confirmed.
 * - Sorted alphabetically by name; capped at [maxResults] to keep the dropdown
 *   manageable.
 */
internal fun filterEntriesForAdd(
	query: String,
	entries: List<SearchableEntry>,
	confirmedIds: Set<Int>,
	dismissedIds: Set<Int>,
	maxResults: Int,
): List<SceneMetadataPanel.AddSuggestion> {
	val q = query.trim().lowercase()
	if (q.isEmpty()) return emptyList()
	return entries
		.asSequence()
		.filter { it.entryDef.id !in confirmedIds }
		.filter { entry -> entry.lowerCaseSearchTerms.any { it.contains(q) } }
		.sortedBy { it.entryDef.name.lowercase() }
		.take(maxResults)
		.map {
			SceneMetadataPanel.AddSuggestion(
				entryDef = it.entryDef,
				isDismissed = it.entryDef.id in dismissedIds,
			)
		}
		.toList()
}
