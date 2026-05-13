package com.darkrockstudios.apps.hammer.common.data.tagindex

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class TagIndexService(
	projectDef: ProjectDef,
	private val encyclopediaRepository: EncyclopediaRepository,
	private val notesRepository: NotesRepository,
	private val timeLineRepository: TimeLineRepository,
	private val sceneEditorRepository: SceneEditorRepository,
) : ScopeCallback, ProjectScoped, KoinComponent {

	override val projectScope = ProjectDefScope(projectDef)

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val dispatcherIo: CoroutineContext by inject(named(DISPATCHER_IO))
	private val serviceScope = CoroutineScope(dispatcherDefault)

	private val _tagIndex = MutableStateFlow(TagIndex.EMPTY)
	val tagIndex: StateFlow<TagIndex> = _tagIndex.asStateFlow()

	private val _isCalculating = MutableStateFlow(false)
	val isCalculating: StateFlow<Boolean> = _isCalculating.asStateFlow()

	// Scene metadata writes also fire for outline/notes/draft-name edits, so filter to
	// emissions where the tag set actually changed - otherwise typing in the outline
	// thrashes the index rebuild.
	private val sceneTagChangedFlow = flow {
		val seenTags = mutableMapOf<Int, Set<String>>()
		sceneEditorRepository.metadataUpdateFlow.collect { (sceneId, metadata) ->
			if (seenTags[sceneId].orEmpty() != metadata.tags) {
				seenTags[sceneId] = metadata.tags
				emit(Unit)
			}
		}
	}

	init {
		projectScope.scope.registerCallback(this)
		serviceScope.launch {
			merge(
				encyclopediaRepository.entryContentChangedFlow,
				notesRepository.noteContentChangedFlow,
				timeLineRepository.eventContentChangedFlow,
				sceneTagChangedFlow,
			)
				.onStart { emit(Unit) }
				.debounce(REBUILD_DEBOUNCE)
				.collect { rebuild() }
		}
	}

	fun getRankedTags(limit: Int = Int.MAX_VALUE): List<TagCount> =
		_tagIndex.value.tagToEntities.toRankedTagCounts(limit) { it.size }

	fun getRankedTags(type: TaggedEntityType, limit: Int = Int.MAX_VALUE): List<TagCount> =
		_tagIndex.value.countsByType[type].orEmpty().toRankedTagCounts(limit) { it }

	fun getEntitiesWithTag(tag: String): Set<TaggedEntityRef> =
		_tagIndex.value.tagToEntities[tag].orEmpty()

	fun suggest(prefix: String, limit: Int = 10): List<TagCount> {
		val needle = prefix.trim()
		if (needle.isEmpty()) return getRankedTags(limit)
		return _tagIndex.value.tagToEntities
			.filterKeys { it.startsWith(needle, ignoreCase = true) }
			.toRankedTagCounts(limit) { it.size }
	}

	private inline fun <V> Map<String, V>.toRankedTagCounts(
		limit: Int,
		countOf: (V) -> Int,
	): List<TagCount> =
		map { TagCount(it.key, countOf(it.value)) }
			.sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.tag })
			.take(limit)

	private suspend fun rebuild() {
		_isCalculating.value = true
		try {
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
						async { scene.id to sceneEditorRepository.loadSceneMetadata(scene.id).tags }
					}.awaitAll()
				}
			}
			for ((sceneId, tags) in sceneTags) {
				val ref = TaggedEntityRef(TaggedEntityType.Scene, sceneId)
				for (tag in tags) {
					accumulate(tagToEntities, countsByType, TaggedEntityType.Scene, tag, ref)
				}
			}

			_tagIndex.value = TagIndex(
				tagToEntities = tagToEntities.mapValues { it.value.toSet() },
				countsByType = countsByType.mapValues { it.value.toMap() },
			)
		} catch (t: Throwable) {
			Napier.e("TagIndexService rebuild failed", t)
		} finally {
			_isCalculating.value = false
		}
	}

	private fun accumulate(
		tagToEntities: MutableMap<String, MutableSet<TaggedEntityRef>>,
		countsByType: MutableMap<TaggedEntityType, MutableMap<String, Int>>,
		type: TaggedEntityType,
		tag: String,
		ref: TaggedEntityRef,
	) {
		tagToEntities.getOrPut(tag) { mutableSetOf() }.add(ref)
		val perType = countsByType.getOrPut(type) { mutableMapOf() }
		perType[tag] = (perType[tag] ?: 0) + 1
	}

	override fun onScopeClose(scope: Scope) {
		serviceScope.cancel("TagIndexService Closed")
	}

	private companion object {
		val REBUILD_DEBOUNCE = 150.milliseconds
	}
}
