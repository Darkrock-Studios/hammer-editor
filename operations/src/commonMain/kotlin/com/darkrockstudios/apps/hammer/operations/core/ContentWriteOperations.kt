package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
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
			)
			result.error.requireNone()
			project.readEntry(def.id)
		}
	},
	operation<ProjectItemInput, EntrySummary>(
		name = "entry.delete",
		description = "Delete an encyclopedia entry, and its image, for good.",
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
	operation<EntryImageSetInput, Entry>(
		name = "entry.image.set",
		description = "Set an encyclopedia entry's image, replacing any it has. The extension is jpg, jpeg, png, or webp, " +
			"and must match the image.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val def = project.requireEntry(input.id)
			if (!project.scope.get<EncyclopediaService>().setEntryImage(def, input.extension, input.content)) {
				val formats = EncyclopediaDatasource.IMAGE_EXTENSIONS.joinToString()
				invalidInput("Images must be one of $formats, and at most ${EncyclopediaDatasource.MAX_IMAGE_SIZE_MB} MB")
			}
			project.readEntry(def.id)
		}
	},
	operation<ProjectItemInput, Entry>(
		name = "entry.image.remove",
		description = "Remove an encyclopedia entry's image.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val def = project.requireEntry(input.id)
			project.scope.get<EncyclopediaService>().removeEntryImage(def)
			project.readEntry(def.id)
		}
	},
	operation<TimelineCreateInput, TimelineEntry>(
		name = "timeline.create",
		description = "Add an event to a project's timeline, at the end unless an index is given.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val timeline = project.scope.get<TimeLineRepository>()
			val count = project.events().size
			if (input.index != null && input.index !in 0..count) invalidInput("index must be from 0 to $count")
			timeline.validateTags(input.tags.toSet()).requireNone()
			val event = timeline.createEvent(content = input.content, date = input.date.orBlankNull(), tags = input.tags.toSet())
			if (input.index != null && input.index != count) project.moveEvent(event.id, input.index)
			TimelineEntry(project.requireEvent(event.id))
		}
	},
	operation<TimelineUpdateInput, TimelineEntry>(
		name = "timeline.update",
		description = "Replace a timeline event's text, and its date or tags when given. An empty date clears it.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val timeline = project.scope.get<TimeLineRepository>()
			val event = project.requireEvent(input.id)
			val tags = input.tags?.toSet() ?: event.tags
			timeline.validateTags(tags).requireNone()
			val date = if (input.date == null) event.date else input.date.orBlankNull()
			timeline.updateEvent(event.copy(content = input.content, date = date, tags = tags))
			TimelineEntry(project.requireEvent(input.id))
		}
	},
	operation<TimelineMoveInput, TimelineEntry>(
		name = "timeline.move",
		description = "Move a timeline event to a position; index is its position after the move.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			project.requireEvent(input.id)
			project.moveEvent(input.id, input.index)
			TimelineEntry(project.requireEvent(input.id))
		}
	},
	operation<ProjectItemInput, TimelineEntry>(
		name = "timeline.delete",
		description = "Delete a timeline event for good.",
		access = Access.Destructive,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val event = project.requireEvent(input.id)
			project.scope.get<TimeLineRepository>().deleteEvent(event)
			TimelineEntry(event)
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

private suspend fun OpenProject.requireEntry(id: Int): EntryDef {
	val encyclopedia = scope.get<EncyclopediaService>()
	encyclopedia.ensureEntriesLoaded()
	return encyclopedia.findEntryDef(id) ?: notFound("No entry $id")
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

private suspend fun OpenProject.events(): List<TimeLineEvent> =
	scope.get<TimeLineRepository>().timelineFlow.loaded("Timeline").events.sortedBy { it.order }

private suspend fun OpenProject.requireEvent(id: Int): TimeLineEvent =
	events().find { it.id == id } ?: notFound("No timeline event $id")

/** Moves the event so it ends up at [index] in timeline order. */
private suspend fun OpenProject.moveEvent(id: Int, index: Int) {
	val events = events()
	if (index !in events.indices) invalidInput("index must be from 0 to ${events.size - 1}")
	val from = events.indexOfFirst { it.id == id }
	if (from == index) return
	// moveEvent inserts after toIndex when moving later, and before it when moving earlier.
	if (!scope.get<TimeLineRepository>().moveEvent(events[from], index, after = index > from)) {
		error("Could not move timeline event $id")
	}
}

private fun TimeLineEventError.requireNone() {
	if (this == TimeLineEventError.TAG_TOO_LONG) invalidInput("A tag is too long")
}

private fun String?.orBlankNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

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
class EntryImageSetInput(
	val project: String,
	val id: Int,
	/** jpg, jpeg, png, or webp. */
	val extension: String,
	@Serializable(with = Base64Bytes::class)
	val content: ByteArray,
)

@Serializable
data class TimelineCreateInput(
	val project: String,
	@FromStdin val content: String,
	/** Free text, such as "Day 3" or "Spring, 1802". */
	val date: String? = null,
	val tags: List<String> = emptyList(),
	/** Its position in the timeline; left out, the end. */
	val index: Int? = null,
)

@Serializable
data class TimelineUpdateInput(
	val project: String,
	val id: Int,
	@FromStdin val content: String,
	/** Left out, unchanged; empty, cleared. */
	val date: String? = null,
	val tags: List<String>? = null,
)

@Serializable
data class TimelineMoveInput(val project: String, val id: Int, val index: Int)
