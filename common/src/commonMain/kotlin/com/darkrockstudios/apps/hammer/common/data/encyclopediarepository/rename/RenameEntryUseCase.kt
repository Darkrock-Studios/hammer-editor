package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.rename

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.globalsearch.SearchProjectUseCase
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.references.BackfillEntryReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.MatchKind
import com.darkrockstudios.apps.hammer.common.data.references.MatchableEntry
import com.darkrockstudios.apps.hammer.common.data.references.NameMatcher
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield

/** The project's entries as loaded when the rename dialog opened, for checking a request as it is edited. */
class RenameContext(val entry: EntryContent, val otherEntries: List<EntryContent>)

/**
 * Renames an entry and rewrites its name and aliases wherever they appear in the project: scene
 * text (archived too), scene titles, outlines and notes, notes, timeline events and entries' text.
 *
 * [preview] finds every match and holds nothing; [apply] searches each place again and changes only
 * the matches still where the preview found them.
 */
class RenameEntryUseCase(
	private val projectDef: ProjectDef,
	private val encyclopediaRepository: EncyclopediaRepository,
	private val encyclopediaService: EncyclopediaService,
	private val sceneEditor: SceneEditorService,
	private val sceneContentRepository: SceneContentRepository,
	private val drafts: SceneDraftRepository,
	private val notesRepository: NotesRepository,
	private val timeLineRepository: TimeLineRepository,
	private val backupRepository: ProjectBackupRepository,
	private val backfillEntryReferences: BackfillEntryReferencesUseCase,
	private val matcher: NameMatcher,
) {
	private class Plan(
		val entry: EntryContent,
		/** Declared form to its new text, for the forms that change. */
		val renames: Map<String, String>,
		/** A changed word of a multi-word form, to its new word; always unsure. */
		val parts: Map<String, String>,
		val matchable: List<MatchableEntry>,
		val entryNames: Map<Int, String>,
	)

	suspend fun loadContext(entryId: Int): RenameContext {
		val entries = loadAllEntries()
		val entry = entries.first { it.id == entryId }
		return RenameContext(entry, entries.filter { it.id != entryId })
	}

	fun check(context: RenameContext, request: RenameRequest): RenameCheck {
		val entry = context.entry
		val newName = newName(request)
		val newAliases = newAliases(request, newName)
		val hasBlankForm = request.allMappings().any { (it.action as? FormAction.Rename)?.to?.isBlank() == true }
		val entryError = encyclopediaRepository.validateEntry(newName, entry.type, entry.text, entry.tags, newAliases)

		val collisions = (listOf(newName) + newAliases).flatMap { form ->
			context.otherEntries
				.filter { other -> form == other.name || form in other.aliases }
				.map { FormCollision(form, it.name) }
		}
		val hasChanges = newName != entry.name ||
			newAliases.toSet() != entry.aliases.toSet() ||
			renames(request).isNotEmpty()

		return RenameCheck(newName, newAliases, entryError, hasBlankForm, collisions, hasChanges)
	}

	suspend fun preview(request: RenameRequest, onProgress: (RenameProgress) -> Unit = {}): RenamePreview {
		val entries = loadAllEntries()
		val plan = buildPlan(request, entries)
		val entryId = plan.entry.id
		val items = mutableListOf<RenameItem>()

		fun scan(key: RenameItemKey, title: String, text: String, dismissed: Boolean = false) {
			val matches = matchText(plan, key, text, dismissed, withSnippets = true)
			if (matches.isNotEmpty()) items.add(RenameItem(key, title, matches))
		}

		val liveNodes = sceneEditor.getSceneTree().map { it.value }.filter { it.type != SceneItem.Type.Root }
		val archived = sceneEditor.getArchivedScenes()
		val notes = notesRepository.loadNotesImperative().map { it.note }
		val events = timeLineRepository.timelineFlow.first().events

		val total = liveNodes.size + archived.size + notes.size + events.size + entries.size
		var done = 0
		fun step() = onProgress(RenameProgress(++done, total))

		for (node in liveNodes) {
			scan(RenameItemKey(RenamePlace.SCENE_TITLE, node.id), node.name, node.name)
			if (node.type == SceneItem.Type.Scene) {
				val metadata = sceneEditor.loadSceneMetadata(node.id)
				val text = runCatching { sceneContentRepository.getCurrentSceneContent(node) }
					.onFailure { Napier.w("Rename: could not read scene ${node.id}", it) }
					.getOrNull()
				if (text != null) {
					scan(RenameItemKey(RenamePlace.SCENE_TEXT, node.id), node.name, text, entryId in metadata.dismissedReferences)
				}
				scan(RenameItemKey(RenamePlace.SCENE_OUTLINE, node.id), node.name, metadata.outline)
				scan(RenameItemKey(RenamePlace.SCENE_NOTES, node.id), node.name, metadata.notes)
			}
			step()
			yield()
		}
		for (scene in archived) {
			val metadata = sceneEditor.loadSceneMetadata(scene.id)
			readArchivedText(scene)?.let { text ->
				scan(
					RenameItemKey(RenamePlace.ARCHIVED_SCENE_TEXT, scene.id),
					scene.name,
					text,
					entryId in metadata.dismissedReferences,
				)
			}
			scan(RenameItemKey(RenamePlace.SCENE_TITLE, scene.id), scene.name, scene.name)
			scan(RenameItemKey(RenamePlace.SCENE_OUTLINE, scene.id), scene.name, metadata.outline)
			scan(RenameItemKey(RenamePlace.SCENE_NOTES, scene.id), scene.name, metadata.notes)
			step()
			yield()
		}
		for (note in notes) {
			scan(RenameItemKey(RenamePlace.NOTE, note.id), firstLine(note.content), note.content)
			step()
		}
		for (event in events) {
			scan(RenameItemKey(RenamePlace.TIMELINE_EVENT, event.id), event.date ?: firstLine(event.content), event.content)
			step()
		}
		for (entry in entries) {
			scan(RenameItemKey(RenamePlace.ENCYCLOPEDIA_ENTRY, entry.id), entry.name, entry.text)
			step()
		}

		val newName = newName(request)
		return RenamePreview(
			request = request,
			oldName = plan.entry.name,
			newName = newName,
			newAliases = newAliases(request, newName),
			items = items.sortedBy { it.key.place.ordinal },
			backupSupported = backupRepository.supportsBackup(),
		)
	}

	/**
	 * Takes a backup, rewrites every accepted match whose text is still where [preview] found it, then
	 * renames the entry. [draftName] names the draft that keeps each rewritten scene's old text.
	 */
	suspend fun apply(
		preview: RenamePreview,
		excludedItems: Set<RenameItemKey>,
		includedUnsure: Set<RenameMatchKey>,
		draftName: String,
		onProgress: (RenameProgress) -> Unit = {},
	): RenameResult {
		val request = preview.request
		val context = loadContext(request.entryId)
		val check = check(context, request)
		if (!check.canProceed) return failed(check.entryError)

		var backupMade = false
		if (backupRepository.supportsBackup()) {
			backupMade = backupRepository.createBackup(projectDef) != null
			if (!backupMade) return failed(EntryError.NONE, backupFailed = true)
		}

		val plan = buildPlan(request, listOf(context.entry) + context.otherEntries)
		val ownKey = RenameItemKey(RenamePlace.ENCYCLOPEDIA_ENTRY, request.entryId)
		val accepted = preview.items
			.filter { it.key !in excludedItems }
			.map { item -> item.key to item.matches.filter { it.isSure || it.key in includedUnsure } }
			.filter { (_, matches) -> matches.isNotEmpty() }

		var replaced = 0
		var itemsChanged = 0
		var skippedStale = 0
		var skippedInvalid = 0

		/** The matches still where the preview found them; the rest are counted as stale. */
		fun stillMatching(key: RenameItemKey, current: String, matches: List<RenameMatch>): List<RenameMatch> {
			val fresh = matchText(plan, key, current, dismissed = false, withSnippets = false)
				.associateBy { it.key }
			val applicable = matches.filter { fresh[it.key]?.replacement == it.replacement }
			skippedStale += matches.size - applicable.size
			return applicable
		}

		notesRepository.loadNotesImperative()
		var ownText: String? = null
		var ownReplaced = 0
		accepted.forEachIndexed { index, (key, matches) ->
			val current = readText(key)
			val applicable = if (current == null) {
				skippedStale += matches.size
				emptyList()
			} else {
				stillMatching(key, current, matches)
			}
			if (current != null && applicable.isNotEmpty()) {
				val newText = replaceAll(current, applicable)
				if (key == ownKey) {
					ownText = newText
					ownReplaced = applicable.size
				} else if (writeText(key, current, newText, draftName)) {
					replaced += applicable.size
					itemsChanged++
				} else {
					skippedInvalid++
				}
			}
			onProgress(RenameProgress(index + 1, accepted.size + 1))
			yield()
		}

		val entry = encyclopediaRepository.loadEntry(request.entryId).entry
		val result = encyclopediaService.updateEntry(
			oldEntryDef = entry.toDef(projectDef),
			name = check.newName,
			text = ownText ?: entry.text,
			tags = entry.tags,
			aliases = check.newAliases,
			excludeFromDictionary = entry.excludeFromDictionary,
		)
		if (ownText != null && result.error == EntryError.NONE) {
			replaced += ownReplaced
			itemsChanged++
		}
		onProgress(RenameProgress(accepted.size + 1, accepted.size + 1))

		// Refreshes the entry list, which also clears the reference matcher's cached names.
		encyclopediaRepository.loadEntriesImperative()
		val renamed = result.instance?.entry
		if (renamed != null) backfillEntryReferences(renamed)

		return RenameResult(
			replaced = replaced,
			itemsChanged = itemsChanged,
			skippedStale = skippedStale,
			skippedInvalid = skippedInvalid,
			backupMade = backupMade,
			backupFailed = false,
			entryError = result.error,
			newEntryDef = renamed?.toDef(projectDef),
		)
	}

	private fun failed(error: EntryError, backupFailed: Boolean = false) = RenameResult(
		replaced = 0,
		itemsChanged = 0,
		skippedStale = 0,
		skippedInvalid = 0,
		backupMade = false,
		backupFailed = backupFailed,
		entryError = error,
		newEntryDef = null,
	)

	private suspend fun loadAllEntries(): List<EntryContent> =
		encyclopediaRepository.loadEntriesImperative().mapNotNull { def ->
			runCatching { encyclopediaRepository.loadEntry(def.id).entry }
				.onFailure { Napier.w("Rename: could not load entry ${def.id}", it) }
				.getOrNull()
		}

	private fun buildPlan(request: RenameRequest, entries: List<EntryContent>): Plan {
		val entry = entries.first { it.id == request.entryId }
		val renames = renames(request)
		val ownForms = listOf(entry.name) + entry.aliases
		val parts = partRenames(renames, ownForms.toSet())
		val matchable = entries.map { e ->
			if (e.id == entry.id) {
				MatchableEntry(e.id, ownForms + parts.keys)
			} else {
				MatchableEntry(e.id, listOf(e.name) + e.aliases)
			}
		}
		return Plan(entry, renames, parts, matchable, entries.associate { it.id to it.name })
	}

	private fun matchText(
		plan: Plan,
		key: RenameItemKey,
		text: String,
		dismissed: Boolean,
		withSnippets: Boolean,
	): List<RenameMatch> {
		if (text.isEmpty()) return emptyList()
		val hits = matcher.findMatches(text, plan.matchable, includeAllCaps = true, includePlurals = true)
		return hits.groupBy { it.range }.mapNotNull { (range, group) ->
			val own = group.firstOrNull { it.entryId == plan.entry.id } ?: return@mapNotNull null
			val target = plan.renames[own.matchedText] ?: plan.parts[own.matchedText] ?: return@mapNotNull null
			val matched = text.substring(range)
			val replacement = when (own.kind) {
				MatchKind.EXACT -> target
				MatchKind.ALL_CAPS -> target.uppercase()
				MatchKind.PLURAL -> target + matched.substring(own.matchedText.length)
			}
			val others = group.filter { it.entryId != plan.entry.id }
				.mapNotNull { plan.entryNames[it.entryId] }
				.distinct()
			val reason = when {
				others.isNotEmpty() -> UnsureReason.SharedWith(others)
				own.matchedText in plan.parts -> UnsureReason.PartOfName(own.matchedText)
				own.kind == MatchKind.PLURAL -> UnsureReason.Plural
				dismissed -> UnsureReason.Dismissed
				else -> null
			}
			val snippet = if (withSnippets && reason != null) {
				SearchProjectUseCase.buildSnippet(text, range.first, matched.length)
			} else {
				null
			}
			RenameMatch(RenameMatchKey(key, range.first, matched), replacement, reason, snippet)
		}
	}

	private fun readArchivedText(scene: SceneItem): String? {
		val path = sceneEditor.resolveScenePathFromFilesystemIncludingArchived(scene.id) ?: return null
		return runCatching { sceneEditor.loadSceneMarkdownRaw(scene, path) }
			.onFailure { Napier.w("Rename: could not read archived scene ${scene.id}", it) }
			.getOrNull()
	}

	private suspend fun readText(key: RenameItemKey): String? = when (key.place) {
		RenamePlace.SCENE_TEXT -> sceneEditor.getSceneItemFromId(key.id)?.let { scene ->
			runCatching { sceneContentRepository.getCurrentSceneContent(scene) }.getOrNull()
		}

		RenamePlace.ARCHIVED_SCENE_TEXT -> archivedScene(key.id)?.let { readArchivedText(it) }
		RenamePlace.SCENE_TITLE -> sceneEditor.getSceneItemFromIdIncludingArchived(key.id)?.name
		RenamePlace.SCENE_OUTLINE -> sceneEditor.loadSceneMetadata(key.id).outline
		RenamePlace.SCENE_NOTES -> sceneEditor.loadSceneMetadata(key.id).notes
		RenamePlace.NOTE -> notesRepository.findNoteForId(key.id)?.content
		RenamePlace.TIMELINE_EVENT -> timeLineRepository.timelineFlow.first().events.find { it.id == key.id }?.content
		RenamePlace.ENCYCLOPEDIA_ENTRY -> runCatching { encyclopediaRepository.loadEntry(key.id).entry.text }.getOrNull()
	}

	/** False when the place refused the new text, which is then left as it was. */
	private suspend fun writeText(key: RenameItemKey, current: String, newText: String, draftName: String): Boolean =
		when (key.place) {
			RenamePlace.SCENE_TEXT -> {
				val scene = sceneEditor.getSceneItemFromId(key.id)
				scene != null &&
					drafts.saveDraft(scene, draftName, current) != null &&
					sceneEditor.replaceSceneText(scene, newText)
			}

			RenamePlace.ARCHIVED_SCENE_TEXT -> {
				val scene = archivedScene(key.id)
				val path = sceneEditor.resolveScenePathFromFilesystemIncludingArchived(key.id)
				scene != null && path != null &&
					drafts.saveDraft(scene, draftName, current) != null &&
					sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, newText), path)
			}

			RenamePlace.SCENE_TITLE -> {
				val scene = sceneEditor.getSceneItemFromIdIncludingArchived(key.id)
				when {
					scene == null -> false
					scene.archived -> sceneEditor.renameArchivedScene(scene, newText)
					else -> sceneEditor.renameScene(scene, newText)
				}
			}

			RenamePlace.SCENE_OUTLINE -> {
				val metadata = sceneEditor.loadSceneMetadata(key.id)
				sceneEditor.storeMetadata(metadata.copy(outline = newText), key.id)
				true
			}

			RenamePlace.SCENE_NOTES -> {
				val metadata = sceneEditor.loadSceneMetadata(key.id)
				sceneEditor.storeMetadata(metadata.copy(notes = newText), key.id)
				true
			}

			RenamePlace.NOTE -> {
				val note = notesRepository.findNoteForId(key.id)
				if (note == null || notesRepository.validateNote(newText, note.tags) != NoteError.NONE) {
					false
				} else {
					notesRepository.updateNote(note.copy(content = newText))
					true
				}
			}

			RenamePlace.TIMELINE_EVENT -> {
				val event = timeLineRepository.timelineFlow.first().events.find { it.id == key.id }
				event != null && timeLineRepository.updateEvent(event.copy(content = newText))
			}

			RenamePlace.ENCYCLOPEDIA_ENTRY -> {
				val entry = encyclopediaRepository.loadEntry(key.id).entry
				encyclopediaService.updateEntry(
					oldEntryDef = entry.toDef(projectDef),
					name = entry.name,
					text = newText,
					tags = entry.tags,
					aliases = entry.aliases,
					excludeFromDictionary = entry.excludeFromDictionary,
				).error == EntryError.NONE
			}
		}

	private fun archivedScene(id: Int): SceneItem? =
		sceneEditor.getSceneItemFromIdIncludingArchived(id)?.takeIf { it.archived }

	companion object {
		private const val TITLE_MAX = 60

		private fun RenameRequest.allMappings(): List<FormMapping> = listOf(name) + aliases

		fun newName(request: RenameRequest): String =
			(request.name.action as? FormAction.Rename)?.to?.trim() ?: request.name.from

		fun newAliases(request: RenameRequest, newName: String): List<String> {
			val targets = request.allMappings().mapNotNull { mapping ->
				when (val action = mapping.action) {
					FormAction.Keep -> mapping.from
					is FormAction.Rename -> action.to.trim()
					FormAction.Remove -> null
				}
			}
			val oldForms = if (request.keepOldNamesAsAliases) request.allMappings().map { it.from } else emptyList()
			return (targets + request.addedAliases.map { it.trim() } + oldForms)
				.filter { it.isNotEmpty() && it != newName }
				.distinct()
		}

		private fun renames(request: RenameRequest): Map<String, String> =
			request.allMappings().mapNotNull { mapping ->
				val to = (mapping.action as? FormAction.Rename)?.to?.trim()
				if (to.isNullOrEmpty() || to == mapping.from) null else mapping.from to to
			}.toMap()

		/**
		 * Where a multi-word form keeps its word count and changes some words ("Robert Tallow" to
		 * "Robert Tolliver"), each changed word ("Tallow") is also looked for alone. A word two forms
		 * would change differently is left out.
		 */
		private fun partRenames(renames: Map<String, String>, ownForms: Set<String>): Map<String, String> {
			val candidates = mutableMapOf<String, MutableSet<String>>()
			for ((from, to) in renames) {
				val fromWords = from.split(WHITESPACE)
				val toWords = to.split(WHITESPACE)
				if (fromWords.size < 2 || fromWords.size != toWords.size) continue
				fromWords.zip(toWords)
					.filter { (f, t) -> f != t && f !in ownForms }
					.forEach { (f, t) -> candidates.getOrPut(f) { mutableSetOf() }.add(t) }
			}
			return candidates.filterValues { it.size == 1 }.mapValues { it.value.single() }
		}

		private fun replaceAll(text: String, matches: List<RenameMatch>): String {
			val builder = StringBuilder(text)
			for (match in matches.sortedByDescending { it.key.offset }) {
				val start = match.key.offset
				builder.setRange(start, start + match.key.matchedText.length, match.replacement)
			}
			return builder.toString()
		}

		private fun firstLine(text: String): String =
			text.trim().lineSequence().firstOrNull().orEmpty().take(TITLE_MAX)

		private val WHITESPACE = Regex("\\s+")
	}
}
