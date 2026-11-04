package com.darkrockstudios.apps.hammer.common.components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.SubjectSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.markdownExcerpt
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.timelineCastRank

/**
 * The references an event of [kind] shows: a placed scene's cast from [subject], else a plain event's
 * [referenceIds] that [catalog] holds, in the order given. An entry placement has none.
 */
internal fun timelineReferenceItems(
	kind: TimelineKind,
	subject: SubjectSummary?,
	referenceIds: Collection<Int>,
	catalog: List<CatalogItem>,
): List<CatalogItem> {
	if (subject is SubjectSummary.Scene) {
		return subject.cast.map { CatalogItem(CatalogKind.Entry, it.id, it.name, entryType = it.type) }
	}
	if (kind != TimelineKind.EVENT) return emptyList()
	val byId = catalog.associateBy { it.id }
	return referenceIds.mapNotNull { byId[it] }
}

/** People, then places, then other entries, then scenes; the order within each is kept. */
internal fun List<CatalogItem>.inCastOrder(): List<CatalogItem> = sortedBy { item ->
	when {
		item.kind != CatalogKind.Entry -> CAST_RANK_SCENE
		else -> item.entryType?.let(::timelineCastRank) ?: CAST_RANK_OTHER_ENTRY
	}
}

/**
 * Up to [EVENT_EXCERPT_CHARS] of [markdown] as plain prose, whitespace collapsed, or null when it has
 * none. A leading [skipTitle] is dropped, for an event already titled by its first line. A project
 * image reads as the name [imageName] gives it.
 */
fun eventExcerpt(markdown: String, skipTitle: String? = null, imageName: (imageId: Int) -> String? = { null }): String? {
	if (markdown.isBlank()) return null
	val preview = markdownExcerpt(markdown, EVENT_EXCERPT_CHARS, imageName)
	val body = if (!skipTitle.isNullOrBlank() && preview.startsWith(skipTitle)) {
		preview.removePrefix(skipTitle).trimStart()
	} else {
		preview
	}
	return body.takeIf { it.isNotBlank() && it != "…" }
}

private const val EVENT_EXCERPT_CHARS = 240

private const val CAST_RANK_OTHER_ENTRY = 2
private const val CAST_RANK_SCENE = 3
