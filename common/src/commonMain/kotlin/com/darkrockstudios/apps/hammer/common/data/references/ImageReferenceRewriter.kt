package com.darkrockstudios.apps.hammer.common.data.references

import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/** An entity whose content refers to an image, or was rewritten because it did. */
data class TouchedEntity(val type: EntityType, val id: Int)

data class ImageUsage(val entity: TouchedEntity, val name: String)

data class ImageUsages(val usages: List<ImageUsage>) {
	val isUnused: Boolean get() = usages.isEmpty()
}

/**
 * Finds and rewrites the references other entities hold to Image entities: Markdown
 * `hammer://image/<id>` links and image id lists. Every rewritten entity is marked dirty for sync.
 * Never throws: an entity that cannot be read or written is logged and skipped.
 */
class ImageReferenceRewriter(projectDef: ProjectDef) : ProjectScoped {
	override val projectScope = ProjectDefScope(projectDef)

	private val encyclopediaRepository: EncyclopediaRepository by projectInject()

	private val holders: List<Holder> = listOf(EntryHolder())

	suspend fun findUsages(imageId: Int): ImageUsages =
		ImageUsages(holders.flatMap { holder -> guarded(holder, emptyList()) { holder.usages(imageId) } })

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
	suspend fun removeAll(imageId: Int): Set<TouchedEntity> = apply(Edit.Remove(imageId))

	private suspend fun apply(edit: Edit): Set<TouchedEntity> =
		holders.flatMapTo(mutableSetOf()) { holder -> guarded(holder, emptySet()) { holder.apply(edit) } }

	private sealed interface Edit {
		fun text(text: String): String
		fun ids(ids: List<Int>): List<Int>

		class Remap(private val idMap: Map<Int, Int>) : Edit {
			override fun text(text: String): String = ImageReferenceSyntax.remap(text, idMap)
			override fun ids(ids: List<Int>): List<Int> =
				if (ids.none { it in idMap }) ids else ids.map { idMap[it] ?: it }.distinct()
		}

		class Remove(private val imageId: Int) : Edit {
			override fun text(text: String): String = ImageReferenceSyntax.remove(text, imageId)
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
			if (entry.uses(imageId)) ImageUsage(entry.touched(), entry.name) else null
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
}
