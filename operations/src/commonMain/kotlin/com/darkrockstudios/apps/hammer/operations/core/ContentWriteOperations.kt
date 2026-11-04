package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineLimits
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineEventDraft
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.Base64Bytes
import com.darkrockstudios.apps.hammer.operations.FromStdin
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.invalidInput
import com.darkrockstudios.apps.hammer.operations.notFound
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.serialization.Serializable

internal fun contentWriteOperations(): List<Operation<*, *>> = listOf(
	operation<NoteCreateInput, Note>(
		name = "note.create",
		description = "Add a note to a project.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val notes = project.scope.get<NotesRepository>()
			notes.requireValid(input.content, input.tags.toSet())
			val created = notes.createNote(input.content, input.tags.toSet())
			Note((created as? ClientResult.Success)?.data ?: error("Could not create the note"))
		}
	},
	operation<NoteUpdateInput, Note>(
		name = "note.update",
		description = "Replace a note's text, and its tags when given.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val notes = project.scope.get<NotesRepository>()
			val note = project.requireNote(input.id)
			val tags = input.tags?.toSet() ?: note.tags
			notes.requireValid(input.content, tags)
			notes.updateNote(note.copy(content = input.content, tags = tags))
			Note(project.requireNote(input.id))
		}
	},
	operation<ProjectItemInput, Note>(
		name = "note.delete",
		description = "Delete a note for good.",
		access = Access.Destructive,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val note = project.requireNote(input.id)
			project.scope.get<NotesRepository>().deleteNote(note.id)
			Note(note)
		}
	},
	operation<EntryCreateInput, Entry>(
		name = "entry.create",
		description = "Add an encyclopedia entry to a project.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val result = project.scope.get<EncyclopediaService>().createEntry(
				name = input.name,
				type = input.type.toType(),
				text = input.text,
				tags = input.tags.toSet(),
				imagePath = null,
				aliases = input.aliases,
			)
			result.error.requireNone()
			project.readEntry(result.instance!!.entry.id)
		}
	},
	operation<EntryUpdateInput, Entry>(
		name = "entry.update",
		description = "Replace an encyclopedia entry's text, and its name, tags, or aliases when given.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val encyclopedia = project.scope.get<EncyclopediaService>()
			val def = project.requireEntry(input.id)
			val current = encyclopedia.loadEntry(def).entry
			val result = encyclopedia.updateEntry(
				oldEntryDef = def,
				name = input.name ?: current.name,
				text = input.text,
				tags = input.tags?.toSet() ?: current.tags,
				aliases = input.aliases ?: current.aliases,
				excludeFromDictionary = current.excludeFromDictionary,
				images = current.images,
			)
			result.error.requireNone()
			project.readEntry(def.id)
		}
	},
	operation<ProjectItemInput, EntrySummary>(
		name = "entry.delete",
		description = "Delete an encyclopedia entry for good. Its images stay in the project.",
		access = Access.Destructive,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val def = project.requireEntry(input.id)
			val encyclopedia = project.scope.get<EncyclopediaService>()
			val aliases = aliasesOf(def, encyclopedia::loadEntry)
			if (!encyclopedia.deleteEntry(def)) error("Could not delete entry ${def.id}")
			EntrySummary(def.id, def.name, EntryKind.of(def.type), aliases)
		}
	},
	operation<EntryImageInput, EntryImages>(
		name = "entry.images.add",
		description = "Add an image already in the project to the end of an encyclopedia entry's images.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val def = project.requireEntry(input.id)
			project.requireImage(input.imageId)
			project.scope.get<EncyclopediaService>().addEntryImage(def, input.imageId)
			project.entryImages(def)
		}
	},
	operation<EntryImageInput, EntryImages>(
		name = "entry.images.remove",
		description = "Take an image off an encyclopedia entry. The image stays in the project.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val def = project.requireEntryImage(input.id, input.imageId)
			project.scope.get<EncyclopediaService>().removeEntryImage(def, input.imageId)
			project.entryImages(def)
		}
	},
	operation<EntryImageInput, EntryImages>(
		name = "entry.images.primary",
		description = "Make one of an encyclopedia entry's images its primary image, moving it first.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val def = project.requireEntryImage(input.id, input.imageId)
			project.scope.get<EncyclopediaService>().setPrimaryImage(def, input.imageId)
			project.entryImages(def)
		}
	},
	operation<EntryImageImportInput, EntryImages>(
		name = "entry.images.import",
		description = "Add a new image to the project, named after an encyclopedia entry, and to the end of the " +
			"entry's images. $IMAGE_FORMAT_RULES",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val def = project.requireEntry(input.id)
			project.scope.get<EncyclopediaService>().importEntryImage(def, input.extension, input.content).requireImage()
			project.entryImages(def)
		}
	},
	operation<TimelineCreateInput, TimelineEntry>(
		name = "timeline.create",
		description = "Add a plain event to a project's timeline, at the end unless an index is given. " +
			"A dated timeline needs a start and takes no index. Entries and scenes are added with timeline.place.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val timeline = project.timelineService()
			val view = timeline.view()
			val count = view.events.size
			if (input.index != null) {
				if (view.def.mode == TimelineMode.DATED) invalidInput(DATED_ORDER)
				if (input.index !in 0..count) invalidInput("index must be from 0 to $count")
			}
			val draft = TimelineEventDraft(
				title = input.title.orBlankNull(),
				label = (input.label ?: input.date).orBlankNull(),
				content = input.content,
				tags = input.tags.toSet(),
				references = input.references.toSet(),
				lane = input.lane,
				milestone = input.milestone,
				start = input.start.toStoryDate(),
				end = input.end.toStoryDate(),
			)
			val event = timeline.createEvent(draft).requireOk() ?: error("The timeline did not return the new event")
			if (input.index != null && input.index != count) project.moveEvent(event.id, input.index)
			project.timelineEntry(event.id)
		}
	},
	operation<TimelineUpdateInput, TimelineEntry>(
		name = "timeline.update",
		description = "Replace a plain event's text, and any other field given; an empty value clears it. " +
			"The text is always required, so every call replaces it. Edit a placement with timeline.set.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val event = project.requireEvent(input.id)
			val label = input.label ?: input.date
			val updated = if (event.kind.isPlacement) {
				val words = input.content.isNotBlank() || !input.title.isNullOrEmpty()
				if (words || !input.tags.isNullOrEmpty() || !input.references.isNullOrEmpty()) {
					invalidInput(PLACEMENT_FIELDS)
				}
				event
			} else {
				event.copy(
					content = input.content,
					title = input.title.orKeep(event.title),
					tags = input.tags?.toSet() ?: event.tags,
					references = input.references?.toSet() ?: event.references,
				)
			}.copy(
				label = label.orKeep(event.label),
				lane = input.lane.orKeep(event.lane),
				milestone = input.milestone ?: event.milestone,
				start = input.start.orKeep(event.start),
				end = input.end.orKeep(event.end),
			)
			project.timelineService().updateEvent(updated).requireOk()
			project.timelineEntry(input.id)
		}
	},
	operation<TimelineMoveInput, TimelineEntry>(
		name = "timeline.move",
		description = "Move a timeline event to a position; index is its position after the move. " +
			"A dated timeline is ordered by its dates, so nothing can be moved on it.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			project.requireEvent(input.id)
			project.moveEvent(input.id, input.index)
			project.timelineEntry(input.id)
		}
	},
	operation<ProjectItemInput, TimelineEntry>(
		name = "timeline.delete",
		description = "Delete a timeline event for good. Deleting a placement leaves its entry or scene alone.",
		access = Access.Destructive,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val event = project.requireEvent(input.id)
			val deleted = TimelineEntry(event, project.timelineService().displayTitle(event))
			project.scope.get<TimeLineRepository>().deleteEvent(event)
			deleted
		}
	},
)

private suspend fun OpenProject.requireNote(id: Int): NoteContent =
	scope.get<NotesRepository>().notesListFlow.loaded("Notes").find { it.note.id == id }?.note
		?: notFound("No note $id")

private fun NotesRepository.requireValid(content: String, tags: Set<String>) {
	when (validateNote(content, tags)) {
		NoteError.NONE -> Unit
		NoteError.EMPTY -> invalidInput("A note needs text")
		NoteError.TOO_LONG -> invalidInput("A note can be at most ${NotesRepository.MAX_NOTE_SIZE} characters")
		NoteError.TAG_TOO_LONG -> invalidInput("A tag is too long")
	}
}

internal suspend fun OpenProject.requireEntry(id: Int): EntryDef {
	val encyclopedia = scope.get<EncyclopediaService>()
	encyclopedia.ensureEntriesLoaded()
	return encyclopedia.findEntryDef(id) ?: notFound("No entry $id")
}

/** The entry [id], which must list image [imageId]. */
private suspend fun OpenProject.requireEntryImage(id: Int, imageId: Int): EntryDef {
	val def = requireEntry(id)
	if (imageId !in scope.get<EncyclopediaService>().loadEntry(def).entry.images) {
		notFound("Entry $id has no image $imageId")
	}
	return def
}

private fun EntryError.requireNone() {
	when (this) {
		EntryError.NONE -> Unit
		EntryError.NAME_TOO_SHORT -> invalidInput("An entry needs a name")
		EntryError.NAME_TOO_LONG -> invalidInput("The name is too long")
		EntryError.NAME_INVALID_CHARACTERS -> invalidInput("The name has characters an entry name cannot")
		EntryError.TAG_TOO_LONG -> invalidInput("A tag is too long")
		EntryError.ALIAS_TOO_LONG -> invalidInput("An alias is too long")
	}
}

/** Moves the event so it ends up at [index] in timeline order. */
private suspend fun OpenProject.moveEvent(id: Int, index: Int) {
	val timeline = timelineService()
	val view = timeline.view()
	if (view.def.mode == TimelineMode.DATED) invalidInput(DATED_ORDER)
	val events = view.events
	if (index !in events.indices) invalidInput("index must be from 0 to ${events.size - 1}")
	val from = events.indexOfFirst { it.id == id }
	if (from == index) return
	// moveEvent inserts after toIndex when moving later, and before it when moving earlier.
	timeline.moveEvent(events[from], index, after = index > from).requireOk()
}

internal const val DATED_ORDER = "The timeline is dated; order follows dates"

internal const val PLACEMENT_FIELDS =
	"A placement takes only label, lane, milestone, start, and end; its title and text come from its entry or scene"

internal fun TimeLineEventError.requireNone() {
	when (this) {
		TimeLineEventError.NONE -> Unit
		TimeLineEventError.TAG_TOO_LONG -> invalidInput("A tag can be at most ${TimelineLimits.MAX_TAG} characters")
		TimeLineEventError.LANE_INVALID -> invalidInput("A lane cannot be blank or longer than ${TimelineLimits.MAX_LANE} characters")
		TimeLineEventError.TOO_MANY_LANES -> invalidInput("A timeline can have at most ${TimelineLimits.MAX_LANES} lanes")
		TimeLineEventError.TITLE_TOO_LONG -> invalidInput("A title can be at most ${TimelineLimits.MAX_TITLE} characters")
		TimeLineEventError.LABEL_TOO_LONG -> invalidInput("A label can be at most ${TimelineLimits.MAX_LABEL} characters")
		TimeLineEventError.SUBJECT_REQUIRED -> invalidInput("A placement needs a subject")
		TimeLineEventError.SUBJECT_NOT_ALLOWED -> invalidInput("A plain event has no subject, and a placement's subject cannot change")
		TimeLineEventError.SUBJECT_WRONG_TYPE -> invalidInput("Only a scene, or an encyclopedia entry of type event, can be placed")
		TimeLineEventError.SUBJECT_NOT_FOUND -> notFound("No such scene or entry to place")
		TimeLineEventError.SUBJECT_ARCHIVED -> invalidInput("Unarchive the scene to place it on the timeline")
		TimeLineEventError.REFERENCE_NOT_FOUND -> invalidInput("A reference names no scene or entry in the project")
		TimeLineEventError.REFERENCES_NOT_ALLOWED -> invalidInput("A placement cannot have references")
		TimeLineEventError.CONTENT_NOT_ALLOWED,
		TimeLineEventError.TAGS_NOT_ALLOWED -> invalidInput(PLACEMENT_FIELDS)
		TimeLineEventError.SELF_REFERENCE -> invalidInput("An event cannot reference itself")
		TimeLineEventError.DUPLICATE_PLACEMENT -> invalidInput("That entry or scene is already placed on that timeline")
		TimeLineEventError.DATE_INVALID -> invalidInput("A date or calendar does not fit; see timeline.settings.read for the calendar")
		TimeLineEventError.DATE_REQUIRED -> invalidInput("A start is required on a dated timeline, and whenever an end is given")
		TimeLineEventError.END_BEFORE_START -> invalidInput("The end is before the start")
		TimeLineEventError.MOVE_NOT_ALLOWED -> invalidInput(DATED_ORDER)
		TimeLineEventError.MOVE_FAILED -> invalidInput("The event cannot move to that position")
		TimeLineEventError.EVENT_NOT_FOUND -> notFound("No such timeline event")
		TimeLineEventError.TIMELINE_NOT_FOUND -> notFound("No such timeline")
		TimeLineEventError.TIMELINE_NAME_INVALID -> invalidInput(
			"A timeline name must be 1 to ${TimelineLimits.MAX_TIMELINE_NAME} characters and differ from every other timeline's, ignoring case"
		)

		TimeLineEventError.MAIN_NOT_DELETABLE -> invalidInput("The main timeline cannot be deleted")
		TimeLineEventError.SAME_TIMELINE -> invalidInput("The event is already on that timeline")
	}
}

/** The event the service returned, which is null for a settings change. */
internal fun TimelineResult.requireOk(): TimeLineEvent? = when (this) {
	is TimelineResult.Ok -> event
	is TimelineResult.Invalid -> {
		error.requireNone()
		throw IllegalStateException("Timeline error NONE reported as invalid")
	}
}

internal fun String?.orBlankNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** Null keeps [current]; blank clears it. */
internal fun String?.orKeep(current: String?): String? = if (this == null) current else orBlankNull()

/** Null keeps [current]; empty clears it. */
internal fun Map<String, Long>?.orKeep(current: StoryDate?): StoryDate? = if (this == null) current else toStoryDate()

internal fun Map<String, Long>?.toStoryDate(): StoryDate? = this?.takeIf { it.isNotEmpty() }?.let(::StoryDate)

@Serializable
data class NoteCreateInput(
	val project: String,
	@FromStdin val content: String,
	val tags: List<String> = emptyList(),
)

@Serializable
data class NoteUpdateInput(
	val project: String,
	val id: Int,
	@FromStdin val content: String,
	/** Left out, the tags stay as they are. */
	val tags: List<String>? = null,
)

@Serializable
data class EntryCreateInput(
	val project: String,
	val name: String,
	val type: EntryKind,
	@FromStdin val text: String,
	val tags: List<String> = emptyList(),
	/** Other names the entry goes by, matched in scenes like its name. */
	val aliases: List<String> = emptyList(),
)

@Serializable
data class EntryUpdateInput(
	val project: String,
	val id: Int,
	@FromStdin val text: String,
	val name: String? = null,
	val tags: List<String>? = null,
	val aliases: List<String>? = null,
)

@Serializable
data class EntryImageInput(
	val project: String,
	/** The entry. */
	val id: Int,
	val imageId: Int,
)

@Serializable
class EntryImageImportInput(
	val project: String,
	/** The entry. */
	val id: Int,
	/** One of [ImageFormat.ALLOWED_EXTENSIONS]. */
	val extension: String,
	@Serializable(with = Base64Bytes::class)
	val content: ByteArray,
)

@Serializable
data class TimelineCreateInput(
	val project: String,
	@FromStdin val content: String,
	/** Deprecated alias of label; label wins when both are given. */
	val date: String? = null,
	val tags: List<String> = emptyList(),
	/** Its position in the timeline; left out, the end. A dated timeline takes none. */
	val index: Int? = null,
	/** Its own title; left out, the content's first line is shown. */
	val title: String? = null,
	/** Free-text "when" a manual timeline shows, such as "Day 3" or "Spring, 1802". */
	val label: String? = null,
	/** The lane it runs in. */
	val lane: String? = null,
	/** A turning point, marked across every lane. */
	val milestone: Boolean = false,
	/** When it starts on the timeline's calendar, such as {"year": 97, "day": 210}; required on a dated timeline. */
	val start: Map<String, Long>? = null,
	/** When it ends, in the same form as start; needs a start. */
	val end: Map<String, Long>? = null,
	/** Scene and entry ids it refers to. */
	val references: List<Int> = emptyList(),
)

/** Every field but the text is left unchanged when left out, and cleared by an empty string, map, or list. */
@Serializable
data class TimelineUpdateInput(
	val project: String,
	val id: Int,
	/** The plain event's markdown; must be empty for a placement, which is edited with timeline.set. */
	@FromStdin val content: String,
	/** Deprecated alias of label; label wins when both are given. */
	val date: String? = null,
	val tags: List<String>? = null,
	val title: String? = null,
	/** Free-text "when" a manual timeline shows. */
	val label: String? = null,
	val lane: String? = null,
	val milestone: Boolean? = null,
	/** When it starts on the timeline's calendar, such as {"year": 97, "day": 210}. */
	val start: Map<String, Long>? = null,
	/** When it ends, in the same form as start. */
	val end: Map<String, Long>? = null,
	/** Scene and entry ids it refers to. */
	val references: List<Int>? = null,
)

@Serializable
data class TimelineMoveInput(val project: String, val id: Int, val index: Int)
