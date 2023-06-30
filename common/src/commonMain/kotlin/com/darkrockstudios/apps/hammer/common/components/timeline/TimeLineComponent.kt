package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.observe
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

class TimeLineComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val updateShouldClose: () -> Unit,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit
) : ProjectComponentBase(projectDef, componentContext), TimeLine {

	private val navigation = StackNavigation<TimeLine.Config>()

	private val _stack = componentContext.childStack(
		source = navigation,
		initialConfiguration = TimeLine.Config.TimeLineOverviewConfig(projectDef = projectDef),
		key = "TimeLineRouter",
		childFactory = ::createChild
	)

	override val stack: Value<ChildStack<TimeLine.Config, TimeLine.Destination>> = _stack

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
			removeMenu = removeMenu
		)
	}

	private fun createViewEvent(
		config: TimeLine.Config.ViewEventConfig,
		componentContext: ComponentContext
	): ViewTimeLineEvent {
		return ViewTimeLineEventComponent(
			componentContext = componentContext,
			projectDef = config.projectDef,
			eventId = config.eventId
		)
	}

	private fun createCreateEvent(
		config: TimeLine.Config.CreateEventConfig,
		componentContext: ComponentContext
	): CreateTimeLineEvent {
		return CreateTimeLineEventComponent(
			componentContext = componentContext,
			projectDef = config.projectDef
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

	override fun isAtRoot(): Boolean {
		return stack.value.active.configuration is TimeLine.Config.TimeLineOverviewConfig
	}

	override fun shouldConfirmClose() = emptySet<CloseConfirm>()

	private val backButtonHandler = object : BackCallback() {
		override fun onBack() {
			if (!isAtRoot()) {
				navigation.pop()
			}
		}
	}

	init {
		backHandler.register(backButtonHandler)

		stack.observe(lifecycle) {
			backButtonHandler.isEnabled = !isAtRoot()
			updateShouldClose()
		}
	}
}