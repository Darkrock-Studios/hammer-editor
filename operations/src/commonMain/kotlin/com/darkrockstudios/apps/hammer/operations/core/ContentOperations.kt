package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryLoadError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import com.darkrockstudios.apps.hammer.common.data.tagindex.normalizeTagNeedle
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.notFound
import com.darkrockstudios.apps.hammer.operations.operation
import io.github.aakira.napier.Napier
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
		description = "A project's encyclopedia entries, with their aliases, optionally only one type or those with a tag.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val tagged = input.tag?.let { tag -> project.tagIndex().entitiesWithTag(tag, TaggedEntityType.Encyclopedia) }
			val encyclopedia = project.scope.get<EncyclopediaRepository>()
			val entries = encyclopedia.ensureEntriesLoaded()
				.filter { input.type == null || EntryKind.of(it.type) == input.type }
				.filter { tagged == null || it.id in tagged }
			Entries(
				entries.map { EntrySummary(it.id, it.name, EntryKind.of(it.type), aliasesOf(it, encyclopedia::loadEntry)) }
					.sortedBy { it.id }
			)
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
	operation<ProjectItemInput, EntryImages>(
		name = "entry.images.list",
		description = "The images an encyclopedia entry shows, primary first.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project -> project.entryImages(project.requireEntry(input.id)) }
	},
	operation<ProjectInput, Timeline>(
		name = "timeline.list",
		description = "A project's timeline events in display order: their manual order, or by start date on a dated timeline.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val timeline = project.timelineService()
			val events = timeline.view().events
			val titles = timeline.displayTitles(events)
			Timeline(events.map { TimelineEntry(it, titles.getValue(it.id)) })
		}
	},
	operation<ProjectItemInput, TimelineEntry>(
		name = "timeline.read",
		description = "One timeline event.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project -> project.timelineEntry(input.id) }
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
		hasImage = entry.images.isNotEmpty(),
		images = entry.images,
		referencedBy = scope.get<ReferenceIndexService>().getScenesReferencing(def.id).sorted(),
	)
}

/** The images [def] lists that are in the project, in its order. */
internal suspend fun OpenProject.entryImages(def: EntryDef): EntryImages {
	val listed = scope.get<EncyclopediaService>().loadEntry(def).entry.images
	val loaded = scope.get<ImagesRepository>().ensureImagesLoaded().associateBy { it.id }
	return EntryImages(listed.mapNotNull { loaded[it] }.map { EntryImageSummary(it.id, it.name, it.fileExtension) })
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

/** An entry's aliases, or none when its file cannot be read, which should not hide the entry. */
internal fun aliasesOf(def: EntryDef, load: (EntryDef) -> EntryContainer): List<String> =
	try {
		load(def).entry.aliases
	} catch (e: EntryLoadError) {
		Napier.w(e) { "Entry ${def.id} could not be read for its aliases" }
		emptyList()
	}

@Serializable
data class EntrySummary(
	val id: Int,
	val name: String,
	val type: EntryKind,
	/** Other names the entry goes by. */
	val aliases: List<String> = emptyList(),
)

@Serializable
data class Entry(
	val id: Int,
	val name: String,
	val type: EntryKind,
	val text: String,
	val tags: List<String>,
	val aliases: List<String>,
	val hasImage: Boolean,
	/** Image ids in order, primary first. An id can name an image no longer in the project. */
	val images: List<Int> = emptyList(),
	/** Scene ids whose references to this entry have been confirmed. */
	val referencedBy: List<Int>,
)

@Serializable
data class EntryImages(val images: List<EntryImageSummary>)

@Serializable
data class EntryImageSummary(
	val id: Int,
	val name: String,
	/** One of [ImageFormat.ALLOWED_EXTENSIONS]. */
	val extension: String,
)

@Serializable
data class Timeline(val events: List<TimelineEntry>)

@Serializable
data class TimelineEntry(
	val id: Int,
	/** Manual position. A dated timeline orders by start date and uses this only to break ties. */
	val order: Int,
	/** Deprecated alias of label, always equal to it. */
	val date: String?,
	/** A plain event's markdown; always empty for a placement. */
	val content: String,
	/** A plain event's tags; always empty for a placement. */
	val tags: List<String>,
	/** A plain event, or the placement of an encyclopedia entry or a scene. */
	val kind: TimelineKindDto = TimelineKindDto.Event,
	/** The placed entry or scene id; null for a plain event. */
	val subject: Int? = null,
	/** A plain event's own title. */
	val title: String? = null,
	/** Free-text "when" a manual timeline shows, such as "Day 3" or "Spring, 1802". */
	val label: String? = null,
	/** The name to show: the subject's name, else the title, the content's first line, or the cached title. */
	val displayTitle: String = "",
	/** The lane it runs in; null for none. */
	val lane: String? = null,
	/** A turning point, marked across every lane. */
	val milestone: Boolean = false,
	/** When it starts on the timeline's calendar, as filled units such as {"year": 97, "day": 210}. */
	val start: Map<String, Long>? = null,
	/** When it ends, in the same form as start; null for a moment. */
	val end: Map<String, Long>? = null,
	/** Scene and entry ids a plain event refers to. */
	val references: List<Int> = emptyList(),
	/** A placement's subject name when last seen, kept in case the subject is deleted. */
	val cachedTitle: String? = null,
) {
	constructor(event: TimeLineEvent, displayTitle: String) : this(
		id = event.id,
		order = event.order,
		date = event.label,
		content = event.content,
		tags = event.tags.sorted(),
		kind = TimelineKindDto.of(event.kind),
		subject = event.subject,
		title = event.title,
		label = event.label,
		displayTitle = displayTitle,
		lane = event.lane,
		milestone = event.milestone,
		start = event.start?.rungs,
		end = event.end?.rungs,
		references = event.references.sorted(),
		cachedTitle = event.cachedTitle,
	)
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
