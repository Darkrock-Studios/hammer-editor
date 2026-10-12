package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchFilter
import com.darkrockstudios.apps.hammer.common.components.globalsearch.SearchResult
import com.darkrockstudios.apps.hammer.common.data.globalsearch.SearchProjectUseCase
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.tagindex.BuildTagIndexUseCase
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndex
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import com.darkrockstudios.apps.hammer.common.data.tagindex.normalizeTagNeedle
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.NoInput
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.invalidInput
import com.darkrockstudios.apps.hammer.operations.jsonSchema
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

internal fun searchOperations(): List<Operation<*, *>> = listOf(
	operation<SearchInput, SearchResults>(
		name = "search",
		description = "Search a project's scenes, notes, encyclopedia, and timeline. #tag in the query filters by tag.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			// The search reads notes from memory, which fills asynchronously once the scope opens.
			project.scope.get<NotesRepository>().notesListFlow.loaded("Notes")
			val results = project.scope.get<SearchProjectUseCase>().search(input.query, input.filter.global)
			SearchResults(results.map(::SearchHit))
		}
	},
	operation<TagListInput, Tags>(
		name = "tag.list",
		description = "The tags used in a project, most used first, optionally counting one kind of entity.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		if (input.limit != null && input.limit < 1) invalidInput("limit must be at least 1")
		projects.withProject(input.project) { project ->
			val index = project.tagIndex()
			val limit = input.limit ?: Int.MAX_VALUE
			val ranked = if (input.kind == null) index.rankedTags(limit) else index.rankedTags(input.kind.tagged, limit)
			Tags(ranked.map { TagCount(it.tag, it.count) })
		}
	},
	operation<TagFindInput, TaggedEntities>(
		name = "tag.find",
		description = "Every scene, note, entry, and timeline event carrying a tag.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val refs = project.tagIndex().tagToEntities[normalizeTagNeedle(input.tag)].orEmpty()
			TaggedEntities(
				refs.map { TaggedEntity(EntityKind.of(it.type), it.id) }
					.sortedWith(compareBy({ it.kind }, { it.id }))
			)
		}
	},
	operation<NoInput, OperationList>(
		name = OPS_LIST,
		description = "Every operation you may call, with JSON schemas for its input and output.",
		access = Access.Read,
		scope = OperationScope.Content,
	) {
		OperationList(operations.operations.map { op -> op.descriptor(op.inputSchema()) })
	},
)

/** Builds the tag index once the notes and timeline it reads from memory have loaded. */
internal suspend fun OpenProject.tagIndex(): TagIndex {
	scope.get<NotesRepository>().notesListFlow.loaded("Notes")
	scope.get<TimeLineRepository>().timelineFlow.loaded("Timeline")
	return scope.get<BuildTagIndexUseCase>()()
}

internal fun TagIndex.entitiesWithTag(tag: String, type: TaggedEntityType): Set<Int> =
	tagToEntities[normalizeTagNeedle(tag)].orEmpty().filter { it.type == type }.mapTo(mutableSetOf()) { it.id }

@Serializable
enum class EntityKind {
	@SerialName("scene") Scene,
	@SerialName("note") Note,
	@SerialName("entry") Entry,
	@SerialName("timeline") Timeline;

	internal val tagged: TaggedEntityType
		get() = when (this) {
			Scene -> TaggedEntityType.Scene
			Note -> TaggedEntityType.Note
			Entry -> TaggedEntityType.Encyclopedia
			Timeline -> TaggedEntityType.TimelineEvent
		}

	companion object {
		internal fun of(type: TaggedEntityType): EntityKind = when (type) {
			TaggedEntityType.Scene -> Scene
			TaggedEntityType.Note -> Note
			TaggedEntityType.Encyclopedia -> Entry
			TaggedEntityType.TimelineEvent -> Timeline
		}
	}
}

@Serializable
enum class SearchFilter(internal val global: GlobalSearchFilter) {
	@SerialName("all") All(GlobalSearchFilter.All),
	@SerialName("scenes") Scenes(GlobalSearchFilter.Scenes),
	@SerialName("notes") Notes(GlobalSearchFilter.Notes),
	@SerialName("encyclopedia") Encyclopedia(GlobalSearchFilter.Encyclopedia),
	@SerialName("timeline") Timeline(GlobalSearchFilter.Timeline),
}

@Serializable
data class SearchInput(val project: String, val query: String, val filter: SearchFilter = SearchFilter.All)

@Serializable
data class SearchResults(val results: List<SearchHit>)

@Serializable
data class SearchHit(
	val kind: EntityKind,
	val id: Int,
	val title: String,
	val snippet: String,
	/** The match's range within [snippet]. */
	val matchStart: Int,
	val matchEnd: Int,
) {
	constructor(result: SearchResult) : this(
		kind = when (result) {
			is SearchResult.Scene -> EntityKind.Scene
			is SearchResult.Note -> EntityKind.Note
			is SearchResult.EncyclopediaEntry -> EntityKind.Entry
			is SearchResult.TimelineEvent -> EntityKind.Timeline
		},
		id = when (result) {
			is SearchResult.Scene -> result.sceneItem.id
			is SearchResult.Note -> result.noteId
			is SearchResult.EncyclopediaEntry -> result.entryDef.id
			is SearchResult.TimelineEvent -> result.eventId
		},
		title = result.title,
		snippet = result.snippet.text,
		matchStart = result.snippet.matchStart,
		matchEnd = result.snippet.matchEnd,
	)
}

@Serializable
data class TagListInput(val project: String, val kind: EntityKind? = null, val limit: Int? = null)

@Serializable
data class Tags(val tags: List<TagCount>)

@Serializable
data class TagCount(val tag: String, val count: Int)

@Serializable
data class TagFindInput(val project: String, val tag: String)

@Serializable
data class TaggedEntities(val entities: List<TaggedEntity>)

@Serializable
data class TaggedEntity(val kind: EntityKind, val id: Int)

const val OPS_LIST = "ops.list"

@Serializable
data class OperationList(val operations: List<OperationDescriptor>)

@Serializable
data class OperationDescriptor(
	val name: String,
	val description: String,
	val access: Access,
	val scope: OperationScope,
	val input: JsonObject,
	val output: JsonObject,
)

/**
 * Describes this operation with [input] as its input schema. The default comes from the input type
 * alone, without the runtime values [Operation.inputSchema] may narrow it with.
 */
fun Operation<*, *>.descriptor(input: JsonObject = jsonSchema(this.input.descriptor)) = OperationDescriptor(
	name = name,
	description = description,
	access = access,
	scope = scope,
	input = input,
	output = jsonSchema(output.descriptor),
)
