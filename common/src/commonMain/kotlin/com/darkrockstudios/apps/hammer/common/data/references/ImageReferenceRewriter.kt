package com.darkrockstudios.apps.hammer.common.data.references

import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.search.markdownTitle
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/** An entity whose content refers to an image, or was rewritten because it did. */
data class TouchedEntity(val type: EntityType, val id: Int)

/** What refers to an image: an entity, or the project's cover, which is not one. */
sealed interface ImageReferrer {
	data class Entity(val entity: TouchedEntity) : ImageReferrer
	data object ProjectCover : ImageReferrer
}

data class ImageUsage(val referrer: ImageReferrer, val name: String)

/** [touched] are the entities a reference was taken out of; [coverCleared] is true when the image was the cover. */
data class ImageRemoval(val touched: Set<TouchedEntity>, val coverCleared: Boolean)

data class ImageUsages(val usages: List<ImageUsage>) {
	val isUnused: Boolean get() = usages.isEmpty()
}

/**
 * Finds and rewrites the references other entities hold to Image entities: Markdown
 * `hammer://image/<id>` links and image id lists, in encyclopedia entries, scenes (active, archived
 * and unsaved edits not yet restored), drafts, notes and timeline events, and the project's cover.
 * Every rewritten entity is marked dirty for sync, except unrestored edits, which are local until
 * restored and saved. Never throws: an entity that cannot be read or written is logged and skipped.
 */
class ImageReferenceRewriter(private val projectDef: ProjectDef) : ProjectScoped {
	override val projectScope = ProjectDefScope(projectDef)

	private val encyclopediaRepository: EncyclopediaRepository by projectInject()
	private val sceneEditor: SceneEditorService by projectInject()
	private val draftRepository: SceneDraftRepository by projectInject()
	private val notesRepository: NotesRepository by projectInject()
	private val timeLineRepository: TimeLineRepository by projectInject()
	private val projectDataRepository: ProjectDataRepository by projectInject()

	private val coverHolder = CoverHolder()

	private val holders: List<Holder> = listOf(
		EntryHolder(),
		SceneHolder(),
		ArchivedSceneHolder(),
		UnrestoredEditsHolder(),
		DraftHolder(),
		NoteHolder(),
		EventHolder(),
		coverHolder,
	)

	/** A scene listed once even when both its text and its unrestored edits use the image. */
	suspend fun findUsages(imageId: Int): ImageUsages = ImageUsages(
		holders
			.flatMap { holder -> guarded(holder, emptyList()) { holder.usages(imageId) } }
			.distinctBy { it.referrer }
	)

	/** Whether anything refers to [imageId]; stops at the first reference. */
	suspend fun isUsed(imageId: Int): Boolean =
		holders.any { holder -> guarded(holder, false) { holder.uses(imageId) } }

	/** The id of every image something refers to, whether or not that image exists. */
	suspend fun findReferencedIds(): Set<Int> =
		holders.flatMapTo(mutableSetOf()) { holder -> guarded(holder, emptySet()) { holder.referencedIds() } }

	/** Rewrites references to each key of [idMap] to its value. */
	suspend fun remap(idMap: Map<Int, Int>): Set<TouchedEntity> {
		val changes = idMap.filter { (old, new) -> old != new }
		if (changes.isEmpty()) return emptySet()
		return apply(Edit.Remap(changes))
	}

	/** Removes every reference to [imageId]. */
	suspend fun removeAll(imageId: Int): ImageRemoval {
		val wasCover = guarded(coverHolder, false) { coverHolder.uses(imageId) }
		val touched = apply(Edit.Remove(imageId))
		val coverCleared = wasCover && !guarded(coverHolder, true) { coverHolder.uses(imageId) }
		return ImageRemoval(touched = touched, coverCleared = coverCleared)
	}

	private suspend fun apply(edit: Edit): Set<TouchedEntity> =
		holders.flatMapTo(mutableSetOf()) { holder -> guarded(holder, emptySet()) { holder.apply(edit) } }

	private sealed interface Edit {
		fun text(text: String): String
		fun ids(ids: List<Int>): List<Int>
		fun id(id: Int): Int?

		class Remap(private val idMap: Map<Int, Int>) : Edit {
			override fun text(text: String): String = ImageReferenceSyntax.remap(text, idMap)
			override fun id(id: Int): Int = idMap[id] ?: id
			override fun ids(ids: List<Int>): List<Int> =
				if (ids.none { it in idMap }) ids else ids.map { idMap[it] ?: it }.distinct()
		}

		class Remove(private val imageId: Int) : Edit {
			override fun text(text: String): String = ImageReferenceSyntax.remove(text, imageId)
			override fun id(id: Int): Int? = id.takeUnless { it == imageId }
			override fun ids(ids: List<Int>): List<Int> =
				if (imageId !in ids) ids else ids.filterNot { it == imageId }
		}
	}

	/** One kind of entity that can refer to images. */
	private interface Holder {
		val name: String
		suspend fun usages(imageId: Int): List<ImageUsage>
		suspend fun uses(imageId: Int): Boolean
		suspend fun referencedIds(): Set<Int>
		suspend fun apply(edit: Edit): Set<TouchedEntity>
	}

	private inner class EntryHolder : Holder {
		override val name = "encyclopedia entries"

		override suspend fun usages(imageId: Int): List<ImageUsage> = eachEntry { def ->
			val entry = encyclopediaRepository.loadEntry(def).entry
			if (entry.uses(imageId)) ImageUsage(ImageReferrer.Entity(entry.touched()), entry.name) else null
		}

		override suspend fun uses(imageId: Int): Boolean = encyclopediaRepository.readEntryDefs().any { def ->
			guarded("entry ${def.id}", false) { encyclopediaRepository.loadEntry(def).entry.uses(imageId) }
		}

		override suspend fun referencedIds(): Set<Int> = eachEntry { def ->
			val entry = encyclopediaRepository.loadEntry(def).entry
			entry.images + ImageReferenceSyntax.findIds(entry.text)
		}.flatten().toSet()

		override suspend fun apply(edit: Edit): Set<TouchedEntity> = eachEntry { def ->
			val changed = encyclopediaRepository.rewriteEntry(def) { entry ->
				entry.copy(text = edit.text(entry.text), images = edit.ids(entry.images))
			}
			if (changed) TouchedEntity(EntityType.EncyclopediaEntry, def.id) else null
		}.toSet()

		private fun EntryContent.touched() = TouchedEntity(EntityType.EncyclopediaEntry, id)

		private fun EntryContent.uses(imageId: Int) =
			imageId in images || imageId in ImageReferenceSyntax.findIds(text)

		private suspend fun <T : Any> eachEntry(block: suspend (EntryDef) -> T?): List<T> =
			encyclopediaRepository.readEntryDefs().mapNotNull { def ->
				guarded("entry ${def.id}", null) { block(def) }
			}
	}

	/**
	 * A kind of entity whose Markdown text can embed images. [rewrite] reads the item's current text,
	 * passes it through the transform and writes the result only when it changed.
	 */
	private abstract inner class MarkdownHolder<T> : Holder {
		abstract val type: EntityType
		abstract suspend fun items(): List<T>
		abstract fun id(item: T): Int
		abstract suspend fun name(item: T): String

		/** The item's text, or null when it has none to read. */
		abstract suspend fun read(item: T): String?

		/** Returns true when the item's text changed and was written. */
		abstract suspend fun rewrite(item: T, transform: (String) -> String): Boolean

		override suspend fun usages(imageId: Int): List<ImageUsage> = eachItem { item ->
			if (read(item)?.let { imageId in ImageReferenceSyntax.findIds(it) } == true) {
				ImageUsage(ImageReferrer.Entity(TouchedEntity(type, id(item))), name(item))
			} else {
				null
			}
		}

		override suspend fun uses(imageId: Int): Boolean = items().any { item ->
			guarded("$name ${id(item)}", false) {
				read(item)?.let { imageId in ImageReferenceSyntax.findIds(it) } == true
			}
		}

		override suspend fun referencedIds(): Set<Int> =
			eachItem { item -> read(item)?.let(ImageReferenceSyntax::findIds) }.flatten().toSet()

		override suspend fun apply(edit: Edit): Set<TouchedEntity> = eachItem { item ->
			if (rewrite(item, edit::text)) TouchedEntity(type, id(item)) else null
		}.toSet()

		private suspend fun <R : Any> eachItem(block: suspend (T) -> R?): List<R> =
			items().mapNotNull { item -> guarded("$name ${id(item)}", null) { block(item) } }
	}

	/** Shared by the holders that store a whole Markdown body: read, transform, write if changed. */
	private inline fun rewriteText(current: String?, transform: (String) -> String, write: (String) -> Boolean): Boolean {
		if (current == null) return false
		val updated = transform(current)
		return updated != current && write(updated)
	}

	/**
	 * Scenes in the tree. A scene with a loaded buffer is rewritten through the buffer, which also
	 * updates an open editor; writing its file directly would be undone by the editor's next save.
	 */
	private inner class SceneHolder : MarkdownHolder<SceneItem>() {
		override val name = "scenes"
		override val type = EntityType.Scene

		override suspend fun items(): List<SceneItem> =
			sceneEditor.getSceneTree().map { it.value }.filter { it.type == SceneItem.Type.Scene }

		override fun id(item: SceneItem) = item.id
		override suspend fun name(item: SceneItem) = item.name
		override suspend fun read(item: SceneItem): String? = sceneEditor.getCurrentSceneContentOrNull(item)

		override suspend fun rewrite(item: SceneItem, transform: (String) -> String): Boolean =
			if (sceneEditor.getSceneBuffer(item) != null) {
				sceneEditor.rewriteSceneText(item, transform)
			} else {
				rewriteFile(item, transform)
			}
	}

	private inner class ArchivedSceneHolder : MarkdownHolder<SceneItem>() {
		override val name = "archived scenes"
		override val type = EntityType.Scene

		override suspend fun items(): List<SceneItem> = sceneEditor.getArchivedScenes()
		override fun id(item: SceneItem) = item.id
		override suspend fun name(item: SceneItem) = item.name

		override suspend fun read(item: SceneItem): String? =
			sceneEditor.resolveScenePathFromFilesystemIncludingArchived(item.id)
				?.let { path -> sceneEditor.loadSceneMarkdownRaw(item, path) }

		override suspend fun rewrite(item: SceneItem, transform: (String) -> String): Boolean =
			rewriteFile(item, transform)
	}

	private suspend fun rewriteFile(scene: SceneItem, transform: (String) -> String): Boolean {
		val path = sceneEditor.resolveScenePathFromFilesystemIncludingArchived(scene.id) ?: return false
		val current = sceneEditor.loadSceneMarkdownRaw(scene, path)
		val updated = transform(current)
		return updated != current && sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, updated), path)
	}

	/**
	 * Unsaved edits an earlier session left on disk that this scope has not restored. Rewritten so that
	 * restoring them does not bring back a reference that was remapped or removed.
	 */
	private inner class UnrestoredEditsHolder : MarkdownHolder<SceneItem>() {
		override val name = "unrestored scene edits"
		override val type = EntityType.Scene

		override suspend fun items(): List<SceneItem> =
			sceneEditor.getSceneTree().map { it.value }
				.filter { it.type == SceneItem.Type.Scene && sceneEditor.hasUnrestoredEdits(it) }

		override fun id(item: SceneItem) = item.id
		override suspend fun name(item: SceneItem) = item.name
		override suspend fun read(item: SceneItem): String? = sceneEditor.loadUnrestoredEdits(item)

		override suspend fun rewrite(item: SceneItem, transform: (String) -> String): Boolean =
			sceneEditor.updateUnrestoredEdits(item, transform)

		/** Reports nothing: syncing the scene would upload its unchanged file over newer server changes. */
		override suspend fun apply(edit: Edit): Set<TouchedEntity> {
			super.apply(edit)
			return emptySet()
		}
	}

	private inner class DraftHolder : MarkdownHolder<DraftDef>() {
		override val name = "drafts"
		override val type = EntityType.SceneDraft

		override suspend fun items(): List<DraftDef> = draftRepository.getAllDrafts().toList()
		override fun id(item: DraftDef) = item.id

		override suspend fun name(item: DraftDef): String {
			val scene = sceneEditor.getSceneItemFromIdIncludingArchived(item.sceneId)
			return if (scene != null) "${scene.name}: ${item.draftName}" else item.draftName
		}

		override suspend fun read(item: DraftDef): String? = draftRepository.loadDraftContent(item)

		override suspend fun rewrite(item: DraftDef, transform: (String) -> String): Boolean =
			rewriteText(read(item), transform) { updated -> draftRepository.updateDraftContent(item, updated) }
	}

	private inner class NoteHolder : MarkdownHolder<NoteContent>() {
		override val name = "notes"
		override val type = EntityType.Note

		override suspend fun items(): List<NoteContent> = notesRepository.loadNotesImperative().map { it.note }
		override fun id(item: NoteContent) = item.id
		override suspend fun name(item: NoteContent) = markdownTitle(item.content, TITLE_LENGTH)
		override suspend fun read(item: NoteContent): String = item.content

		override suspend fun rewrite(item: NoteContent, transform: (String) -> String): Boolean =
			rewriteText(item.content, transform) { updated ->
				notesRepository.updateNote(item.copy(content = updated))
				true
			}
	}

	private inner class EventHolder : MarkdownHolder<TimeLineEvent>() {
		override val name = "timeline events"
		override val type = EntityType.TimelineEvent

		override suspend fun items(): List<TimeLineEvent> = timeLineRepository.loadTimeline().events
		override fun id(item: TimeLineEvent) = item.id
		override suspend fun name(item: TimeLineEvent) = markdownTitle(item.content, TITLE_LENGTH)
		override suspend fun read(item: TimeLineEvent): String = item.content

		override suspend fun rewrite(item: TimeLineEvent, transform: (String) -> String): Boolean =
			rewriteText(item.content, transform) { updated ->
				timeLineRepository.updateEvent(item.copy(content = updated))
			}
	}

	/**
	 * The project's cover. Written through [ProjectDataRepository.updateData] and never the datasource:
	 * the project data sync reads the repository's cached state, so a write behind it would be lost.
	 */
	private inner class CoverHolder : Holder {
		override val name = "project cover"

		private suspend fun cover(): Int? = projectDataRepository.load().data.coverImageId

		override suspend fun usages(imageId: Int): List<ImageUsage> =
			if (cover() == imageId) listOf(ImageUsage(ImageReferrer.ProjectCover, projectDef.name)) else emptyList()

		override suspend fun uses(imageId: Int): Boolean = cover() == imageId

		override suspend fun referencedIds(): Set<Int> = setOfNotNull(cover())

		/** Reports nothing: project data syncs on its own path, and [ProjectDataRepository.updateData] flags it for that. */
		override suspend fun apply(edit: Edit): Set<TouchedEntity> {
			projectDataRepository.updateData { data ->
				data.copy(coverImageId = data.coverImageId?.let(edit::id))
			}
			return emptySet()
		}
	}

	private suspend fun <T> guarded(holder: Holder, fallback: T, block: suspend () -> T): T =
		guarded(holder.name, fallback, block)

	@Suppress("TooGenericExceptionCaught")
	private suspend fun <T> guarded(what: String, fallback: T, block: suspend () -> T): T =
		try {
			block()
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Napier.e("Image reference rewrite failed for $what", e)
			fallback
		}

	private companion object {
		// Notes and events have no title: their first line of prose stands in, cut short.
		const val TITLE_LENGTH = 40
	}
}
