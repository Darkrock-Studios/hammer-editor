package com.darkrockstudios.apps.hammer.common.data.tagindex

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext

/**
 * Loads tag data from notes, timeline events, encyclopedia entries, and scene
 * metadata, then aggregates it into a [TagIndex]. Stateless; safe to call from
 * any caller that wants a one-shot snapshot.
 */
class BuildTagIndexUseCase(
	private val encyclopediaRepository: EncyclopediaRepository,
	private val notesRepository: NotesRepository,
	private val timeLineRepository: TimeLineRepository,
	private val sceneEditorRepository: SceneRepository,
	private val sceneMetadataRepository: SceneMetadataRepository,
) : KoinComponent {

	private val dispatcherIo: CoroutineContext by inject(named(DISPATCHER_IO))

	suspend operator fun invoke(): TagIndex {
		val tagToEntities = mutableMapOf<String, MutableSet<TaggedEntityRef>>()
		val countsByType = mutableMapOf<TaggedEntityType, MutableMap<String, Int>>()

		val noteContainers = notesRepository.notesListFlow.first()
		for (container in noteContainers) {
			val ref = TaggedEntityRef(TaggedEntityType.Note, container.note.id)
			for (tag in container.note.tags) {
				accumulate(tagToEntities, countsByType, TaggedEntityType.Note, tag, ref)
			}
		}

		val timeline = timeLineRepository.timelineFlow.first()
		for (event in timeline.events) {
			val ref = TaggedEntityRef(TaggedEntityType.TimelineEvent, event.id)
			for (tag in event.tags) {
				accumulate(tagToEntities, countsByType, TaggedEntityType.TimelineEvent, tag, ref)
			}
		}

		val entryDefs = encyclopediaRepository.ensureEntriesLoaded()
		val entries = coroutineScope {
			entryDefs.map { def ->
				async { def.id to encyclopediaRepository.loadEntry(def).entry.tags }
			}.awaitAll()
		}
		for ((entryId, tags) in entries) {
			val ref = TaggedEntityRef(TaggedEntityType.Encyclopedia, entryId)
			for (tag in tags) {
				accumulate(tagToEntities, countsByType, TaggedEntityType.Encyclopedia, tag, ref)
			}
		}

		val sceneTags = withContext(dispatcherIo) {
			val sceneItems = sceneEditorRepository.getScenes()
				.filter { it.type == SceneItem.Type.Scene } + sceneEditorRepository.getArchivedScenes()
			coroutineScope {
				sceneItems.map { scene ->
					async { scene.id to sceneMetadataRepository.loadSceneMetadata(scene.id).tags }
				}.awaitAll()
			}
		}
		for ((sceneId, tags) in sceneTags) {
			val ref = TaggedEntityRef(TaggedEntityType.Scene, sceneId)
			for (tag in tags) {
				accumulate(tagToEntities, countsByType, TaggedEntityType.Scene, tag, ref)
			}
		}

		return TagIndex(
			tagToEntities = tagToEntities.mapValues { it.value.toSet() },
			countsByType = countsByType.mapValues { it.value.toMap() },
		)
	}

	// Normalized on the way in rather than trusting every writer to have called cleanTags: a tag
	// that reached disk decomposed would otherwise be a key no typed needle can ever match.
	private fun accumulate(
		tagToEntities: MutableMap<String, MutableSet<TaggedEntityRef>>,
		countsByType: MutableMap<TaggedEntityType, MutableMap<String, Int>>,
		type: TaggedEntityType,
		tag: String,
		ref: TaggedEntityRef,
	) {
		val key = normalizeTagNeedle(tag)
		if (key.isEmpty()) return
		tagToEntities.getOrPut(key) { mutableSetOf() }.add(ref)
		val perType = countsByType.getOrPut(type) { mutableMapOf() }
		perType[key] = (perType[key] ?: 0) + 1
	}
}
