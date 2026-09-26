package com.darkrockstudios.apps.hammer.common.components.encyclopedia.rename

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.rename.*

interface RenameEntry {

	val state: Value<State>

	data class State(
		val step: Step = Step.Loading,
		val name: FormEdit = FormEdit(""),
		val aliases: List<FormEdit> = emptyList(),
		val addedAliases: List<String> = emptyList(),
		val keepOldNames: Boolean = false,
		val check: RenameCheck? = null,
	) {
		/** Only before anything is written or after it is done; never while applying. */
		val canClose: Boolean get() = step !is Step.Applying
	}

	/** One of the entry's current forms and what the user has made of it. */
	data class FormEdit(val from: String, val text: String = from, val removed: Boolean = false)

	sealed interface Step {
		data object Loading : Step
		data object Names : Step
		data class Searching(val progress: RenameProgress?) : Step

		data class Summary(
			val preview: RenamePreview,
			val excludedItems: Set<RenameItemKey> = emptySet(),
			val includedUnsure: Set<RenameMatchKey> = emptySet(),
		) : Step {
			val pendingCount: Int
				get() = preview.items
					.filter { it.key !in excludedItems }
					.sumOf { item -> item.matches.count { it.isSure || it.key in includedUnsure } }

			val unsureCount: Int get() = preview.items.sumOf { it.unsure.size }
		}

		data class Applying(val progress: RenameProgress?) : Step
		data class Done(val result: RenameResult) : Step
	}

	fun updateName(text: String)
	fun updateAlias(index: Int, text: String)
	fun setAliasRemoved(index: Int, removed: Boolean)
	fun addAlias()
	fun updateAddedAlias(index: Int, text: String)
	fun removeAddedAlias(index: Int)
	fun setKeepOldNames(keep: Boolean)

	fun findReferences()
	fun backToNames()
	fun setItemExcluded(key: RenameItemKey, excluded: Boolean)
	fun setUnsureIncluded(key: RenameMatchKey, included: Boolean)
	fun apply()
	fun close()
}
