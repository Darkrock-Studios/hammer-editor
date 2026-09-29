package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.rename

import com.darkrockstudios.apps.hammer.common.components.globalsearch.AnnotatedSnippet
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef

sealed interface FormAction {
	data object Keep : FormAction
	data class Rename(val to: String) : FormAction

	/** Drops an alias from the entry; the text that uses it is left alone. */
	data object Remove : FormAction
}

data class FormMapping(val from: String, val action: FormAction)

/** Several mappings renamed to the same form merge them into one. */
data class RenameRequest(
	val entryId: Int,
	val name: FormMapping,
	val aliases: List<FormMapping>,
	val addedAliases: List<String> = emptyList(),
	val keepOldNamesAsAliases: Boolean = false,
)

data class FormCollision(val form: String, val otherEntryName: String)

data class RenameCheck(
	val newName: String,
	val newAliases: List<String>,
	val entryError: EntryError,
	val hasBlankForm: Boolean,
	val collisions: List<FormCollision>,
	val hasChanges: Boolean,
) {
	val canProceed: Boolean get() = entryError == EntryError.NONE && !hasBlankForm && hasChanges
}

enum class RenamePlace {
	SCENE_TEXT,
	ARCHIVED_SCENE_TEXT,
	SCENE_TITLE,
	SCENE_OUTLINE,
	SCENE_NOTES,
	NOTE,
	TIMELINE_EVENT,
	ENCYCLOPEDIA_ENTRY,
}

data class RenameItemKey(val place: RenamePlace, val id: Int)

/** Names a match by where it was found and the text it covered, so a later edit can be detected. */
data class RenameMatchKey(val item: RenameItemKey, val offset: Int, val matchedText: String)

sealed interface UnsureReason {
	data object Plural : UnsureReason
	data class PartOfName(val word: String) : UnsureReason
	data class SharedWith(val otherEntryNames: List<String>) : UnsureReason
	data object Dismissed : UnsureReason
}

data class RenameMatch(
	val key: RenameMatchKey,
	val replacement: String,
	val reason: UnsureReason?,
	val snippet: AnnotatedSnippet?,
) {
	val isSure: Boolean get() = reason == null
}

data class RenameItem(
	val key: RenameItemKey,
	val title: String,
	val matches: List<RenameMatch>,
) {
	val sureCount: Int get() = matches.count { it.isSure }
	val unsure: List<RenameMatch> get() = matches.filterNot { it.isSure }
}

data class RenamePreview(
	val request: RenameRequest,
	val oldName: String,
	val newName: String,
	val newAliases: List<String>,
	val items: List<RenameItem>,
	val backupSupported: Boolean,
)

data class RenameProgress(val done: Int, val total: Int)

data class RenameResult(
	val replaced: Int,
	val itemsChanged: Int,
	val skippedStale: Int,
	val skippedInvalid: Int,
	val backupMade: Boolean,
	val backupFailed: Boolean,
	val entryError: EntryError,
	val newEntryDef: EntryDef?,
)
