package com.darkrockstudios.apps.hammer.common.components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.SubjectSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent

/** What the list needs to know about a placement's subject without reading its text. */
internal data class SubjectGlance(val chapterPath: String?, val missing: Boolean)

/**
 * [SubjectGlance] for every placement in [events], by event id, reading the scene tree, the archive
 * and the entry list at most once each. Blocking: call it off the main thread.
 */
internal suspend fun glanceSubjects(
	events: List<TimeLineEvent>,
	sceneRepository: SceneRepository,
	encyclopediaRepository: EncyclopediaRepository,
): Map<Int, SubjectGlance> {
	val scenes = events.filter { it.kind == TimelineKind.SCENE }
	val entries = events.filter { it.kind == TimelineKind.ENTRY }
	if (scenes.isEmpty() && entries.isEmpty()) return emptyMap()

	val glances = HashMap<Int, SubjectGlance>()
	if (scenes.isNotEmpty()) {
		val tree = sceneRepository.rawTree
		val archived by lazy { sceneRepository.getArchivedScenes().associateBy { it.id } }
		scenes.forEach { event ->
			val sceneId = event.subject
			val branch = sceneId?.let { id -> tree.getBranchOrNull(excludeLeaf = false) { it.id == id } }
			glances[event.id] = when {
				sceneId == null -> SubjectGlance(null, missing = true)
				branch != null -> {
					val scene = branch.last().value
					val path = branch.dropLast(1)
						.map { it.value }
						.filter { it.type == SceneItem.Type.Group }
						.joinToString(CHAPTER_SEPARATOR) { it.name }
					SubjectGlance(path.ifEmpty { null }, missing = scene.type != SceneItem.Type.Scene)
				}

				else -> SubjectGlance(null, missing = archived[sceneId]?.type != SceneItem.Type.Scene)
			}
		}
	}
	if (entries.isNotEmpty()) {
		val entryIds = encyclopediaRepository.readEntryDefs().mapTo(HashSet()) { it.id }
		entries.forEach { event ->
			glances[event.id] = SubjectGlance(null, missing = event.subject !in entryIds)
		}
	}
	return glances
}

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
		item.kind != CatalogKind.Entry -> 3
		item.entryType == EntryType.PERSON -> 0
		item.entryType == EntryType.PLACE -> 1
		else -> 2
	}
}

// As TimelineService joins a chapter path.
private const val CHAPTER_SEPARATOR = " › "
