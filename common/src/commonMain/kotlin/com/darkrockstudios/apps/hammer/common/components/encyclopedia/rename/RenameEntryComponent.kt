package com.darkrockstudios.apps.hammer.common.components.encyclopedia.rename

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.rename.RenameEntry.FormEdit
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.rename.RenameEntry.Step
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.rename.*
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.encyclopedia_entry_rename_draft_name
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class RenameEntryComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val entryId: Int,
	private val onRenamed: (EntryDef) -> Unit,
	private val onClose: () -> Unit,
) : ProjectComponentBase(projectDef, componentContext), RenameEntry {

	private val renameEntry: RenameEntryUseCase by projectInject()
	private val strRes: StrRes by inject()

	private val _state = MutableValue(RenameEntry.State())
	override val state: Value<RenameEntry.State> = _state

	private var context: RenameContext? = null
	private var searchJob: Job? = null

	/** Back belongs to the dialog while it is open, so it can refuse while a rename is being written. */
	private val backCallback = BackCallback { close() }

	override fun onCreate() {
		super.onCreate()
		backHandler.register(backCallback)
		scope.launch {
			val loaded = renameEntry.loadContext(entryId)
			withContext(dispatcherMain) {
				context = loaded
				_state.getAndUpdate {
					it.copy(
						step = Step.Names,
						name = FormEdit(loaded.entry.name),
						aliases = loaded.entry.aliases.map { alias -> FormEdit(alias) },
					)
				}
				recheck()
			}
		}
	}

	private fun editNames(transform: (RenameEntry.State) -> RenameEntry.State) {
		_state.getAndUpdate(transform)
		recheck()
	}

	private fun recheck() {
		val loaded = context ?: return
		_state.getAndUpdate { it.copy(check = renameEntry.check(loaded, request(it))) }
	}

	private fun request(state: RenameEntry.State) = RenameRequest(
		entryId = entryId,
		name = state.name.toMapping(),
		aliases = state.aliases.map { it.toMapping() },
		addedAliases = state.addedAliases,
		keepOldNamesAsAliases = state.keepOldNames,
	)

	private fun FormEdit.toMapping(): FormMapping = FormMapping(
		from = from,
		action = when {
			removed -> FormAction.Remove
			text.trim() == from -> FormAction.Keep
			else -> FormAction.Rename(text)
		},
	)

	override fun updateName(text: String) = editNames { it.copy(name = it.name.copy(text = text)) }

	override fun updateAlias(index: Int, text: String) = editNames {
		it.copy(aliases = it.aliases.mapIndexed { i, alias -> if (i == index) alias.copy(text = text) else alias })
	}

	override fun setAliasRemoved(index: Int, removed: Boolean) = editNames {
		it.copy(aliases = it.aliases.mapIndexed { i, alias -> if (i == index) alias.copy(removed = removed) else alias })
	}

	override fun addAlias() = editNames { it.copy(addedAliases = it.addedAliases + "") }

	override fun updateAddedAlias(index: Int, text: String) = editNames {
		it.copy(addedAliases = it.addedAliases.mapIndexed { i, alias -> if (i == index) text else alias })
	}

	override fun removeAddedAlias(index: Int) = editNames {
		it.copy(addedAliases = it.addedAliases.filterIndexed { i, _ -> i != index })
	}

	override fun setKeepOldNames(keep: Boolean) = editNames { it.copy(keepOldNames = keep) }

	override fun findReferences() {
		val current = state.value
		if (current.step != Step.Names || current.check?.canProceed != true) return
		val request = request(current)
		_state.getAndUpdate { it.copy(step = Step.Searching(null)) }
		searchJob = scope.launch {
			val preview = renameEntry.preview(request) { progress ->
				_state.getAndUpdate { state ->
					if (state.step is Step.Searching) state.copy(step = Step.Searching(progress)) else state
				}
			}
			withContext(dispatcherMain) {
				_state.getAndUpdate { it.copy(step = Step.Summary(preview)) }
			}
		}
	}

	override fun backToNames() {
		when (state.value.step) {
			is Step.Searching, is Step.Summary -> {
				searchJob?.cancel()
				_state.getAndUpdate { it.copy(step = Step.Names) }
			}

			else -> Unit
		}
	}

	private fun updateSummary(transform: (Step.Summary) -> Step.Summary) {
		_state.getAndUpdate { state ->
			val summary = state.step as? Step.Summary ?: return@getAndUpdate state
			state.copy(step = transform(summary))
		}
	}

	override fun setItemExcluded(key: RenameItemKey, excluded: Boolean) = updateSummary {
		it.copy(excludedItems = if (excluded) it.excludedItems + key else it.excludedItems - key)
	}

	override fun setUnsureIncluded(key: RenameMatchKey, included: Boolean) = updateSummary {
		it.copy(includedUnsure = if (included) it.includedUnsure + key else it.includedUnsure - key)
	}

	override fun apply() {
		val summary = state.value.step as? Step.Summary ?: return
		_state.getAndUpdate { it.copy(step = Step.Applying(null)) }
		scope.launch {
			// Once writing starts it runs to the end, so closing cannot leave a rename half done.
			val result = withContext(NonCancellable) {
				val draftName = strRes.get(Res.string.encyclopedia_entry_rename_draft_name, summary.preview.oldName)
				renameEntry.apply(
					preview = summary.preview,
					excludedItems = summary.excludedItems,
					includedUnsure = summary.includedUnsure,
					draftName = draftName,
				) { progress ->
					_state.getAndUpdate { it.copy(step = Step.Applying(progress)) }
				}
			}
			withContext(dispatcherMain) {
				result.newEntryDef?.let(onRenamed)
				_state.getAndUpdate { it.copy(step = Step.Done(result)) }
			}
		}
	}

	override fun close() {
		if (state.value.canClose) onClose()
	}
}
