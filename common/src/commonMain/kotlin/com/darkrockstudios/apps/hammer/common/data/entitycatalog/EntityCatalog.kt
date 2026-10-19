package com.darkrockstudios.apps.hammer.common.data.entitycatalog

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.tagindex.normalizeTagNeedle

/** The kinds of item a picker offers, by the ids plugins name them with. */
enum class CatalogKind(val id: String) {
	Scene("scene"),
	Note("note"),
	Entry("entry"),
	Event("event");

	companion object {
		fun of(id: String): CatalogKind? = entries.firstOrNull { it.id == id }
	}
}

/** Which item: a [kind] and its [id] among those. */
data class CatalogRef(val kind: CatalogKind, val id: Int)

/** One item of a project to pick: a scene, note, encyclopedia entry, or timeline event. */
data class CatalogItem(
	val kind: CatalogKind,
	val id: Int,
	/** A scene's or entry's name, a note's first line, or an event's date or first line. */
	val name: String,
	val aliases: List<String> = emptyList(),
	/** Only for an entry. */
	val entryType: EntryType? = null,
	/** Normalized, as the tag index holds them. */
	val tags: Set<String> = emptySet(),
	/** What tells it from another of the same name: a scene's groups, or an event's first line when its date names it. */
	val detail: String? = null,
) {
	val ref: CatalogRef get() = CatalogRef(kind, id)
}

/**
 * Which items may be picked: those of [kinds]; entries only of [entryTypes], when there are any; and
 * only items carrying at least one of [tags], when there are any.
 */
data class CatalogFilter(
	val kinds: Set<CatalogKind> = CatalogKind.entries.toSet(),
	val entryTypes: Set<EntryType> = emptySet(),
	val tags: Set<String> = emptySet(),
) {
	private val wantedTags = tags.mapTo(HashSet()) { normalizeTagNeedle(it).lowercase() }

	fun accepts(item: CatalogItem): Boolean =
		item.kind in kinds &&
			(item.kind != CatalogKind.Entry || entryTypes.isEmpty() || item.entryType in entryTypes) &&
			(wantedTags.isEmpty() || item.tags.any { it.lowercase() in wantedTags })
}

/** A project's items to pick from, read afresh on each call. */
interface EntityCatalog {
	/** Every item of [kinds]: scenes in story order, entries and notes by name, and events in timeline order. */
	suspend fun items(kinds: Set<CatalogKind> = CatalogKind.entries.toSet()): List<CatalogItem>
}

/**
 * The [items] [filter] accepts whose name or an alias holds [query], ignoring case, in the order
 * given. A blank query matches every one.
 */
fun filterCatalog(items: List<CatalogItem>, query: String, filter: CatalogFilter): List<CatalogItem> {
	val needle = query.trim()
	return items.filter { item ->
		filter.accepts(item) &&
			(needle.isEmpty() || item.name.contains(needle, ignoreCase = true) || item.aliases.any { it.contains(needle, ignoreCase = true) })
	}
}

fun List<CatalogItem>.find(ref: CatalogRef): CatalogItem? = firstOrNull { it.kind == ref.kind && it.id == ref.id }
