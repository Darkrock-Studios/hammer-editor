package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import com.darkrockstudios.apps.hammer.common.data.tagindex.normalizeTagNeedle
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.Base64Bytes
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.notFound
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

internal fun contentOperations(): List<Operation<*, *>> = listOf(
	operation<NoteListInput, Notes>(
		name = "note.list",
		description = "A project's notes, optionally only those with a tag.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val notes = project.scope.get<NotesRepository>().notesListFlow.loaded("Notes").map { it.note }
			Notes(notes.filter { input.tag == null || it.tags.hasTag(input.tag) }.map(::Note))
		}
	},
	operation<ProjectItemInput, Note>(
		name = "note.read",
		description = "One note.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val notes = project.scope.get<NotesRepository>().notesListFlow.loaded("Notes")
			val note = notes.find { it.note.id == input.id } ?: notFound("No note ${input.id}")
			Note(note.note)
		}
	},
	operation<EntryListInput, Entries>(
		name = "entry.list",
		description = "A project's encyclopedia entries, optionally only one type or those with a tag.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val tagged = input.tag?.let { tag -> project.tagIndex().entitiesWithTag(tag, TaggedEntityType.Encyclopedia) }
			val entries = project.scope.get<EncyclopediaRepository>().ensureEntriesLoaded()
				.filter { input.type == null || EntryKind.of(it.type) == input.type }
				.filter { tagged == null || it.id in tagged }
			Entries(entries.map { EntrySummary(it.id, it.name, EntryKind.of(it.type)) }.sortedBy { it.id })
		}
	},
	operation<ProjectItemInput, Entry>(
		name = "entry.read",
		description = "An encyclopedia entry, with the scenes confirmed to reference it.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project -> project.readEntry(input.id) }
	},
	operation<ProjectItemInput, EntryImage>(
		name = "entry.image.get",
		description = "An encyclopedia entry's image.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val encyclopedia = project.scope.get<EncyclopediaRepository>()
			encyclopedia.ensureEntriesLoaded()
			val def = encyclopedia.findEntryDef(input.id) ?: notFound("No entry ${input.id}")
			val extension = encyclopedia.findEntryImageExtension(def) ?: notFound("Entry ${input.id} has no image")
			EntryImage(extension, encyclopedia.loadEntryImage(def, extension))
		}
	},
	operation<ProjectInput, Timeline>(
		name = "timeline.list",
		description = "A project's timeline events in order.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val timeline = project.scope.get<TimeLineRepository>().timelineFlow.loaded("Timeline")
			Timeline(timeline.events.sortedBy { it.order }.map(::TimelineEntry))
		}
	},
	operation<ProjectItemInput, TimelineEntry>(
		name = "timeline.read",
		description = "One timeline event.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val timeline = project.scope.get<TimeLineRepository>().timelineFlow.loaded("Timeline")
			val event = timeline.events.find { it.id == input.id } ?: notFound("No timeline event ${input.id}")
			TimelineEntry(event)
		}
	},
	operation<IdeaListInput, Ideas>(
		name = "idea.list",
		description = "Story ideas, which belong to the account rather than a project.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		val ideas = koinGet<IdeasRepository>().ideasFlow.loaded("Ideas")
			.filter { input.archived == null || (it.archived != null) == input.archived }
		Ideas(ideas.map(::Idea))
	},
)

/** The entry as entry.read shows it. */
internal suspend fun OpenProject.readEntry(id: Int): Entry {
	val encyclopedia = scope.get<EncyclopediaRepository>()
	encyclopedia.ensureEntriesLoaded()
	val def = encyclopedia.findEntryDef(id) ?: notFound("No entry $id")
	val entry = encyclopedia.loadEntry(def).entry
	return Entry(
		id = entry.id,
		name = entry.name,
		type = EntryKind.of(entry.type),
		text = entry.text,
		tags = entry.tags.sorted(),
		aliases = entry.aliases,
		hasImage = encyclopedia.findEntryImageExtension(def) != null,
		referencedBy = scope.get<ReferenceIndexService>().getScenesReferencing(def.id).sorted(),
	)
}

/** Matches tags the way the tag index keys them, so filters agree with tag.list and tag.find. */
private fun Set<String>.hasTag(tag: String): Boolean {
	val needle = normalizeTagNeedle(tag)
	return any { normalizeTagNeedle(it) == needle }
}

@Serializable
data class NoteListInput(val project: String, val tag: String? = null)

@Serializable
data class Notes(val notes: List<Note>)

@Serializable
data class Note(val id: Int, val created: Instant, val tags: List<String>, val content: String) {
	constructor(note: NoteContent) : this(note.id, note.created, note.tags.sorted(), note.content)
}

@Serializable
enum class EntryKind {
	@SerialName("person") Person,
	@SerialName("place") Place,
	@SerialName("thing") Thing,
	@SerialName("event") Event,
	@SerialName("idea") Idea;

	fun toType(): EntryType = when (this) {
		Person -> EntryType.PERSON
		Place -> EntryType.PLACE
		Thing -> EntryType.THING
		Event -> EntryType.EVENT
		Idea -> EntryType.IDEA
	}

	companion object {
		fun of(type: EntryType): EntryKind = when (type) {
			EntryType.PERSON -> Person
			EntryType.PLACE -> Place
			EntryType.THING -> Thing
			EntryType.EVENT -> Event
			EntryType.IDEA -> Idea
		}
	}
}

@Serializable
data class EntryListInput(val project: String, val type: EntryKind? = null, val tag: String? = null)

@Serializable
data class Entries(val entries: List<EntrySummary>)

@Serializable
data class EntrySummary(val id: Int, val name: String, val type: EntryKind)

@Serializable
data class Entry(
	val id: Int,
	val name: String,
	val type: EntryKind,
	val text: String,
	val tags: List<String>,
	val aliases: List<String>,
	val hasImage: Boolean,
	/** Scene ids whose references to this entry have been confirmed. */
	val referencedBy: List<Int>,
)

@Serializable
class EntryImage(
	/** jpg, jpeg, png, or webp. */
	val extension: String,
	@Serializable(with = Base64Bytes::class)
	val content: ByteArray,
)

@Serializable
data class Timeline(val events: List<TimelineEntry>)

@Serializable
data class TimelineEntry(
	val id: Int,
	val order: Int,
	/** Free text; timelines are not bound to a calendar. */
	val date: String?,
	val content: String,
	val tags: List<String>,
) {
	constructor(event: TimeLineEvent) : this(event.id, event.order, event.date, event.content, event.tags.sorted())
}

@Serializable
data class IdeaListInput(
	/** True for only archived ideas, false for only active ones; omitted for both. */
	val archived: Boolean? = null,
)

@Serializable
data class Ideas(val ideas: List<Idea>)

@Serializable
data class Idea(
	val id: String,
	val title: String?,
	val content: String,
	val tags: List<String>,
	val created: Instant,
	val updated: Instant,
	val promoted: Instant?,
	val archived: Instant?,
) {
	constructor(idea: StoryIdea) : this(
		id = idea.id.id,
		title = idea.title,
		content = idea.content,
		tags = idea.tags.sorted(),
		created = idea.created,
		updated = idea.updated,
		promoted = idea.promoted,
		archived = idea.archived,
	)
}
