package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.subscribe
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistrar
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.neighbour
import com.darkrockstudios.apps.hammer.common.components.actions.registerWhileStarted
import com.darkrockstudios.apps.hammer.common.components.actions.stepAction
import com.darkrockstudios.apps.hammer.common.components.replaceTop
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef

class EncyclopediaComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val updateShouldClose: () -> Unit,
	private val actions: ActionRegistrar,
	private val showScene: (SceneItem) -> Unit,
	private val onShowGlobalSearchForTag: (String) -> Unit,
	private val showTimelineEvent: (eventId: Int) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), Encyclopedia {

	private val navigation = StackNavigation<Encyclopedia.Config>()
	override val stack: Value<ChildStack<Encyclopedia.Config, Encyclopedia.Destination>>

	private fun createChild(
		config: Encyclopedia.Config,
		componentContext: ComponentContext
	): Encyclopedia.Destination =
		when (config) {
			is Encyclopedia.Config.BrowseEntriesConfig -> Encyclopedia.Destination.BrowseEntriesDestination(
				createBrowseEntries(config, componentContext)
			)

			is Encyclopedia.Config.ViewEntryConfig -> Encyclopedia.Destination.ViewEntryDestination(
				createViewEntry(config, componentContext)
			)

			is Encyclopedia.Config.CreateEntryConfig -> Encyclopedia.Destination.CreateEntryDestination(
				createCreateEntry(config, componentContext)
			)
		}

	override fun showBrowse() {
		navigation.popWhile { it !is Encyclopedia.Config.BrowseEntriesConfig }
	}

	override fun showViewEntry(entryDef: EntryDef) {
		navigation.bringToFront(Encyclopedia.Config.ViewEntryConfig(entryDef))
	}

	override fun showCreateEntry() {
		navigation.push(Encyclopedia.Config.CreateEntryConfig(projectDef))
	}

	override fun isAtRoot(): Boolean {
		return stack.value.active.configuration is Encyclopedia.Config.BrowseEntriesConfig
	}

	override fun onBack() {
		if (!isAtRoot()) {
			navigation.pop()
		}
	}

	override fun shouldConfirmClose(): Set<CloseConfirm> {
		val unsaved = stack.value.items.any { isUnsaved(it.instance) }
		return if (unsaved) setOf(CloseConfirm.Encyclopedia) else emptySet()
	}

	private fun isUnsaved(destination: Encyclopedia.Destination) = when (destination) {
		is Encyclopedia.Destination.CreateEntryDestination -> true
		is Encyclopedia.Destination.ViewEntryDestination -> isEditing(destination.component)
		else -> false
	}

	private fun isEditing(entry: ViewEntry) = entry.state.value.let { it.editName || it.editText }

	override fun discardUnsaved(item: CloseConfirm) {
		if (item != CloseConfirm.Encyclopedia) return
		stack.value.items.forEach { child ->
			val view = child.instance as? Encyclopedia.Destination.ViewEntryDestination ?: return@forEach
			if (isEditing(view.component)) view.component.discardEdit()
		}
		if (stack.value.active.instance is Encyclopedia.Destination.CreateEntryDestination) {
			navigation.pop()
		}
	}

	private fun createBrowseEntries(
		config: Encyclopedia.Config.BrowseEntriesConfig,
		componentContext: ComponentContext
	): BrowseEntries {
		return BrowseEntriesComponent(
			componentContext = componentContext,
			projectDef = config.projectDef
		)
	}

	private fun createViewEntry(
		config: Encyclopedia.Config.ViewEntryConfig,
		componentContext: ComponentContext
	): ViewEntry {
		return ViewEntryComponent(
			componentContext = componentContext,
			entryDef = config.entryDef,
			closeEntry = ::closeEntry,
			showScene = showScene,
			onShowGlobalSearchForTag = onShowGlobalSearchForTag,
			onShowTimelineEvent = showTimelineEvent,
		)
	}

	private fun createCreateEntry(
		config: Encyclopedia.Config.CreateEntryConfig,
		componentContext: ComponentContext
	): CreateEntry {
		return CreateEntryComponent(
			componentContext = componentContext,
			projectDef = config.projectDef
		)
	}

	private fun closeEntry() {
		navigation.pop()
	}

	private val browse: BrowseEntries?
		get() = stack.value.items.firstNotNullOfOrNull {
			(it.instance as? Encyclopedia.Destination.BrowseEntriesDestination)?.component
		}

	/**
	 * Steps the open entry by [offset] through the filtered list. Not live during an edit: the
	 * edit buffer lives in the UI, so replacing the view would drop it unasked.
	 */
	private fun entryStep(offset: Int): ActionHandler = stepAction(
		target = target@{
			val view = stack.value.active.instance as? Encyclopedia.Destination.ViewEntryDestination ?: return@target null
			if (isEditing(view.component)) return@target null
			val open = (stack.value.active.configuration as Encyclopedia.Config.ViewEntryConfig).entryDef
			browse?.getFilteredEntries()?.neighbour(offset) { it.id == open.id }
		},
		go = { entry -> navigation.replaceTop(Encyclopedia.Config.ViewEntryConfig(entry)) },
	)

	init {
		actions.registerWhileStarted(
			lifecycle,
			ActionLayer.Module,
			mapOf(
				HammerActions.ItemNew.id to ActionHandler(
					isEnabled = { stack.value.active.configuration !is Encyclopedia.Config.CreateEntryConfig },
				) { showCreateEntry() },
				HammerActions.ItemNext.id to entryStep(1),
				HammerActions.ItemPrev.id to entryStep(-1),
			),
		)
		stack = componentContext.childStack(
			source = navigation,
			initialConfiguration = Encyclopedia.Config.BrowseEntriesConfig(projectDef = projectDef),
			key = "EncyclopediaRouter",
			childFactory = ::createChild,
			serializer = Encyclopedia.Config.serializer(),
		)
		stack.subscribe(lifecycle) {
			updateShouldClose()
		}
	}
}