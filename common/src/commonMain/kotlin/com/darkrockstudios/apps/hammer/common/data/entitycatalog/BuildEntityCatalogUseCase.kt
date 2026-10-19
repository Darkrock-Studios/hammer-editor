package com.darkrockstudios.apps.hammer.common.data.entitycatalog

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.search.markdownTitleLine
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import com.darkrockstudios.apps.hammer.common.data.tagindex.normalizeTagNeedle
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
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
 * Lists a project's items to pick from. Scenes, notes, and events are in memory already; each entry
 * is read for its aliases and tags. Scenes take their tags from the tag index, since their metadata
 * is on disk. Archived scenes are left out, as they are not in the tree.
 */
class BuildEntityCatalogUseCase(
	private val encyclopedia: EncyclopediaRepository,
	private val scenes: SceneRepository,
	private val notes: NotesRepository,
	private val timeline: TimeLineRepository,
	private val tagIndex: TagIndexService,
) : EntityCatalog, KoinComponent {

	private val dispatcherIo: CoroutineContext by inject(named(DISPATCHER_IO))

	override suspend fun items(kinds: Set<CatalogKind>): List<CatalogItem> = coroutineScope {
		val parts = CatalogKind.entries.filter { it in kinds }.map { kind ->
			async {
				when (kind) {
					CatalogKind.Scene -> sceneItems()
					CatalogKind.Note -> noteItems()
					CatalogKind.Entry -> entryItems()
					CatalogKind.Event -> eventItems()
				}
			}
		}
		parts.awaitAll().flatten()
	}

	private fun sceneItems(): List<CatalogItem> {
		val tags = HashMap<Int, MutableSet<String>>()
		tagIndex.tagIndex.value.tagToEntities.forEach { (tag, refs) ->
			refs.filter { it.type == TaggedEntityType.Scene }.forEach { tags.getOrPut(it.id) { mutableSetOf() } += tag }
		}
		return scenes.getSceneTree().root.children.flatMap { it.sceneItems(groups = emptyList(), tags) }
	}

	private fun TreeValue<SceneItem>.sceneItems(groups: List<String>, tags: Map<Int, Set<String>>): List<CatalogItem> = when (value.type) {
		SceneItem.Type.Scene -> listOf(
			CatalogItem(
				kind = CatalogKind.Scene,
				id = value.id,
				name = value.name,
				tags = tags[value.id].orEmpty(),
				detail = groups.takeIf { it.isNotEmpty() }?.joinToString(GROUP_SEPARATOR),
			)
		)
		else -> children.flatMap { it.sceneItems(groups + value.name, tags) }
	}

	private suspend fun noteItems(): List<CatalogItem> = notes.notesListFlow.first()
		.map { container ->
			val note = container.note
			CatalogItem(
				kind = CatalogKind.Note,
				id = note.id,
				name = titleOf(note.content) ?: EMPTY_NOTE,
				tags = normalized(note.tags),
			)
		}
		.sortedBy { it.name.lowercase() }

	private suspend fun entryItems(): List<CatalogItem> {
		val defs = encyclopedia.ensureEntriesLoaded()
		val items = withContext(dispatcherIo) {
			defs.map { def ->
				async {
					val entry = try {
						encyclopedia.loadEntry(def).entry
					} catch (e: CancellationException) {
						throw e
					} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
						// Still pickable by name; it only goes without its aliases and tags.
						Napier.w(e) { "Could not read entry ${def.id} for the entity catalog" }
						null
					}
					CatalogItem(
						kind = CatalogKind.Entry,
						id = def.id,
						name = def.name,
						aliases = entry?.aliases.orEmpty().filter { it.isNotBlank() },
						entryType = def.type,
						tags = normalized(entry?.tags.orEmpty()),
					)
				}
			}.awaitAll()
		}
		return items.sortedBy { it.name.lowercase() }
	}

	private suspend fun eventItems(): List<CatalogItem> = timeline.timelineFlow.first().events.map { event ->
		val date = event.date?.trim()?.takeIf { it.isNotEmpty() }
		val title = titleOf(event.content)
		CatalogItem(
			kind = CatalogKind.Event,
			id = event.id,
			name = date ?: title ?: EMPTY_EVENT,
			tags = normalized(event.tags),
			detail = title.takeIf { date != null },
		)
	}

	private fun titleOf(markdown: String): String? {
		val title = markdownTitleLine(markdown)
		return when {
			title.isEmpty() -> null
			title.length > TITLE_MAX -> title.take(TITLE_MAX).trimEnd() + "…"
			else -> title
		}
	}

	private fun normalized(tags: Set<String>): Set<String> =
		tags.mapNotNullTo(LinkedHashSet()) { tag -> normalizeTagNeedle(tag).takeIf { it.isNotEmpty() } }

	private companion object {
		const val TITLE_MAX = 60
		const val GROUP_SEPARATOR = " › "

		// As global search titles them.
		const val EMPTY_NOTE = "(empty note)"
		const val EMPTY_EVENT = "(empty event)"
	}
}
