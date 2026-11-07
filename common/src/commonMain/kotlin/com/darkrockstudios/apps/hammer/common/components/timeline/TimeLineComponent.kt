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
import com.arkivanov.decompose.router.stack.pushToFront
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.subscribe
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineSelectionSetting
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.timelineId
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class TimeLineComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val updateShouldClose: () -> Unit,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
	private val onShowGlobalSearchForTag: (String) -> Unit,
	private val onShowScene: (SceneItem) -> Unit,
	private val onShowEntry: (EntryDef) -> Unit,
	private val onShowTimelineSettings: (timelineId: String) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), TimeLine {

	private val timelineService: TimelineService by projectInject()
	private val selectionSetting: TimelineSelectionSetting by inject()

	private val _selection = MutableValue(restoredSelection())
	override val selection: Value<TimelineSelection> = _selection

	private val dialogs = TimelineDefDialogs(
		timelineService = timelineService,
		scope = scope,
		mainContext = dispatcherMain,
		onCreated = { selectTimeline(TimelineSelection.Def(it.id)) },
		onDeleted = { if (_selection.value == TimelineSelection.Def(it.id)) selectTimeline(TimelineSelection.Main) },
	)
	override val switcher: Value<TimelineSwitcher> = dialogs.state

	// The state keeper first, so a restore keeps the deleted-timeline bucket; then the last timeline shown.
	private fun restoredSelection(): TimelineSelection =
		stateKeeper.consume(SELECTION_KEY, TimelineSelection.serializer())
			?: selectionSetting.load(projectDef.id)?.let { TimelineSelection.Def(it) }
			?: TimelineSelection.Main

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
			selection = selection,
			addMenu = addMenu,
			removeMenu = removeMenu,
			onRequestNewTimeline = ::requestCreateTimeline,
			onSelectTimeline = ::selectTimeline,
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
			onShowEventOn = ::showEventOn,
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
			onShowEventOn = ::showEventOn,
		)
	}

	private fun createCreateEvent(
		config: TimeLine.Config.CreateEventConfig,
		componentContext: ComponentContext
	): CreateTimeLineEvent {
		return CreateTimeLineEventComponent(
			componentContext = componentContext,
			projectDef = config.projectDef,
			timelineId = config.timelineId,
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
		val selected = _selection.value as? TimelineSelection.Def ?: return
		navigation.push(TimeLine.Config.CreateEventConfig(projectDef, selected.id))
	}

	override fun showEventDialog(eventId: Int) {
		dialogNavigation.activate(TimeLine.EventDialogConfig(projectDef, eventId))
	}

	override fun closeEventDialog() {
		eventDialog.value.child?.instance?.confirmClose()
	}

	private val overview: TimeLineOverview?
		get() = stack.value.items.firstNotNullOfOrNull { (it.instance as? TimeLine.Destination.TimeLineOverviewDestination)?.component }

	// The overview may be showing another timeline than the event's.
	private fun onSelectedTimeline(eventId: Int): Boolean =
		overview?.state?.value?.rows?.any { it.event.id == eventId } == true

	override fun canFrameEvent(eventId: Int): Boolean =
		onSelectedTimeline(eventId) && overview?.canFrame(eventId) == true

	override fun frameEvent(eventId: Int) {
		if (!onSelectedTimeline(eventId)) return
		overview?.setFrame(TimelineFrame.Band(eventId))
		dialogNavigation.dismiss()
	}

	override fun selectTimeline(selection: TimelineSelection) {
		if (_selection.value == selection) return
		_selection.value = selection
		if (selection is TimelineSelection.Def) selectionSetting.save(projectDef.id, selection.id)
	}

	override fun showEvent(eventId: Int) {
		scope.launch {
			val event = timelineService.event(eventId) ?: return@launch
			val def = timelineService.definition(event.timelineId)
			withContext(dispatcherMain) {
				selectTimeline(def?.let { TimelineSelection.Def(it.id) } ?: TimelineSelection.Orphans)
				navigation.pushToFront(TimeLine.Config.ViewEventConfig(projectDef, eventId))
			}
		}
	}

	override fun showEventOn(timelineId: String, eventId: Int) {
		selectTimeline(TimelineSelection.Def(timelineId))
		if (eventDialog.value.child != null) {
			dialogNavigation.activate(TimeLine.EventDialogConfig(projectDef, eventId))
		} else {
			navigation.pushToFront(TimeLine.Config.ViewEventConfig(projectDef, eventId))
		}
	}

	override fun showTimelineSettings() {
		onShowTimelineSettings((_selection.value as? TimelineSelection.Def)?.id ?: TimelineDef.MAIN_ID)
	}

	override fun requestCreateTimeline() = dialogs.requestCreate()

	override fun createTimeline(name: String, mode: TimelineMode, preset: CalendarPreset) =
		dialogs.create(name, mode, preset)

	override fun requestRenameTimeline(timelineId: String) {
		scope.launch {
			val def = timelineService.definition(timelineId) ?: return@launch
			withContext(dispatcherMain) { dialogs.requestRename(def) }
		}
	}

	override fun renameTimeline(name: String) = dialogs.rename(name)

	override fun requestDeleteTimeline(timelineId: String) {
		scope.launch {
			val summary = timelineService.summaries.first().firstOrNull { it.def.id == timelineId } ?: return@launch
			withContext(dispatcherMain) { dialogs.requestDelete(summary) }
		}
	}

	override fun confirmDeleteTimeline() = dialogs.confirmDelete()

	override fun dismissTimelineDialog() = dialogs.dismiss()

	/** Falls back to main once the selected timeline is gone, or the deleted-timeline bucket is empty. */
	private fun watchSelectionStillThere() {
		scope.launch {
			combine(timelineService.timelines, timelineService.orphansFlow) { defs, orphans ->
				defs.mapTo(HashSet()) { it.id } to orphans.isNotEmpty()
			}.collect { (live, hasOrphans) ->
				withContext(dispatcherMain) {
					val gone = when (val current = _selection.value) {
						is TimelineSelection.Def -> current.id !in live
						TimelineSelection.Orphans -> !hasOrphans
					}
					if (gone) selectTimeline(TimelineSelection.Main)
				}
			}
		}
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

	override fun onCreate() {
		super.onCreate()
		watchSelectionStillThere()
	}

	init {
		stateKeeper.register(SELECTION_KEY, TimelineSelection.serializer()) { _selection.value }
		backHandler.register(dialogBackCallback)
		stack.subscribe(lifecycle) {
			updateShouldClose()
		}
		eventDialog.subscribe(lifecycle) {
			dialogBackCallback.isEnabled = it.child != null
			updateShouldClose()
		}
	}

	private companion object {
		const val SELECTION_KEY = "timeline_selected"
	}
}
