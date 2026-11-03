package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.popWhile
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.subscribe
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef

class TimeLineComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val updateShouldClose: () -> Unit,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
	private val onShowGlobalSearchForTag: (String) -> Unit,
	private val onShowScene: (SceneItem) -> Unit,
	private val onShowEntry: (EntryDef) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), TimeLine {

	private val navigation = StackNavigation<TimeLine.Config>()

	private val _stack = componentContext.childStack(
		source = navigation,
		initialConfiguration = TimeLine.Config.TimeLineOverviewConfig(projectDef = projectDef),
		key = "TimeLineRouter",
		childFactory = ::createChild,
		serializer = TimeLine.Config.serializer(),
	)

	override val stack: Value<ChildStack<TimeLine.Config, TimeLine.Destination>> = _stack

	private val dialogNavigation = SlotNavigation<TimeLine.EventDialogConfig>()

	override val eventDialog: Value<ChildSlot<TimeLine.EventDialogConfig, ViewTimeLineEvent>> =
		componentContext.childSlot(
			source = dialogNavigation,
			serializer = TimeLine.EventDialogConfig.serializer(),
			key = "TimeLineEventDialog",
			handleBackButton = false,
			childFactory = ::createEventDialog,
		)

	private val dialogBackCallback = BackCallback(isEnabled = false) { closeEventDialog() }

	private fun createChild(
		config: TimeLine.Config,
		componentContext: ComponentContext
	): TimeLine.Destination =
		when (config) {
			is TimeLine.Config.TimeLineOverviewConfig -> TimeLine.Destination.TimeLineOverviewDestination(
				createOverview(config, componentContext)
			)

			is TimeLine.Config.ViewEventConfig -> TimeLine.Destination.ViewEventDestination(
				createViewEvent(config, componentContext)
			)

			is TimeLine.Config.CreateEventConfig -> TimeLine.Destination.CreateEventDestination(
				createCreateEvent(config, componentContext)
			)
		}

	private fun createOverview(
		config: TimeLine.Config.TimeLineOverviewConfig,
		componentContext: ComponentContext
	): TimeLineOverview {
		return TimeLineOverviewComponent(
			componentContext = componentContext,
			projectDef = config.projectDef,
			addMenu = addMenu,
			removeMenu = removeMenu,
			onShowScene = onShowScene,
			onShowEntry = onShowEntry,
		)
	}

	private fun createViewEvent(
		config: TimeLine.Config.ViewEventConfig,
		componentContext: ComponentContext
	): ViewTimeLineEvent {
		return ViewTimeLineEventComponent(
			componentContext = componentContext,
			projectDef = config.projectDef,
			eventId = config.eventId,
			onCloseEvent = ::showOverview,
			addMenu = addMenu,
			removeMenu = removeMenu,
			updateShouldClose = updateShouldClose,
			onShowGlobalSearchForTag = onShowGlobalSearchForTag,
			onShowScene = onShowScene,
			onShowEntry = onShowEntry,
		)
	}

	private fun createEventDialog(
		config: TimeLine.EventDialogConfig,
		componentContext: ComponentContext
	): ViewTimeLineEvent {
		return ViewTimeLineEventComponent(
			componentContext = componentContext,
			projectDef = config.projectDef,
			eventId = config.eventId,
			onCloseEvent = dialogNavigation::dismiss,
			addMenu = addMenu,
			removeMenu = removeMenu,
			updateShouldClose = updateShouldClose,
			onShowGlobalSearchForTag = { tag ->
				dialogNavigation.dismiss()
				onShowGlobalSearchForTag(tag)
			},
			onShowScene = { scene ->
				dialogNavigation.dismiss()
				onShowScene(scene)
			},
			onShowEntry = { entry ->
				dialogNavigation.dismiss()
				onShowEntry(entry)
			},
		)
	}

	private fun createCreateEvent(
		config: TimeLine.Config.CreateEventConfig,
		componentContext: ComponentContext
	): CreateTimeLineEvent {
		return CreateTimeLineEventComponent(
			componentContext = componentContext,
			projectDef = config.projectDef,
			onClose = ::showOverview
		)
	}

	override fun showOverview() {
		navigation.popWhile { it !is TimeLine.Config.TimeLineOverviewConfig }
	}

	override fun showViewEvent(eventId: Int) {
		navigation.push(TimeLine.Config.ViewEventConfig(projectDef, eventId))
	}

	override fun showCreateEvent() {
		navigation.push(TimeLine.Config.CreateEventConfig(projectDef))
	}

	override fun showEventDialog(eventId: Int) {
		dialogNavigation.activate(TimeLine.EventDialogConfig(projectDef, eventId))
	}

	override fun closeEventDialog() {
		eventDialog.value.child?.instance?.confirmClose()
	}

	override fun isAtRoot(): Boolean {
		return stack.value.active.configuration is TimeLine.Config.TimeLineOverviewConfig &&
			eventDialog.value.child == null
	}

	override fun onBack() {
		when {
			eventDialog.value.child != null -> closeEventDialog()
			!isAtRoot() -> navigation.pop()
		}
	}

	override fun shouldConfirmClose(): Set<CloseConfirm> {
		val dialogUnsaved = eventDialog.value.child?.instance?.isEditingAndDirty() == true
		val unsaved = dialogUnsaved || stack.value.items.any { isUnsaved(it.instance) }
		return if (unsaved) setOf(CloseConfirm.Timeline) else emptySet()
	}

	private fun isUnsaved(destination: TimeLine.Destination) = when (destination) {
		is TimeLine.Destination.CreateEventDestination -> true
		is TimeLine.Destination.ViewEventDestination -> destination.component.isEditingAndDirty()
		else -> false
	}

	override fun discardUnsaved(item: CloseConfirm) {
		if (item != CloseConfirm.Timeline) return
		eventDialog.value.child?.instance?.let { dialog ->
			if (dialog.state.value.isEditing) dialog.discardEdit()
		}
		stack.value.items.forEach { child ->
			val view = child.instance as? TimeLine.Destination.ViewEventDestination ?: return@forEach
			if (view.component.state.value.isEditing) view.component.discardEdit()
		}
		if (stack.value.active.instance is TimeLine.Destination.CreateEventDestination) {
			navigation.pop()
		}
	}

	init {
		backHandler.register(dialogBackCallback)
		stack.subscribe(lifecycle) {
			updateShouldClose()
		}
		eventDialog.subscribe(lifecycle) {
			dialogBackCallback.isEnabled = it.child != null
			updateShouldClose()
		}
	}
}