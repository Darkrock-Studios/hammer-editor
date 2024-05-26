package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projectroot.Router
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import kotlinx.serialization.Serializable

interface TimeLine : Router {

	val stack: Value<ChildStack<Config, Destination>>

	sealed class Destination {
		data class TimeLineOverviewDestination(val component: TimeLineOverview) : Destination()

		data class ViewEventDestination(val component: ViewTimeLineEvent) : Destination()

		data class CreateEventDestination(val component: CreateTimeLineEvent) : Destination()
	}

	@Serializable
	sealed class Config {
		@Serializable
		data class TimeLineOverviewConfig(val projectDef: ProjectDef) : Config()

		@Serializable
		data class ViewEventConfig(val projectDef: ProjectDef, val eventId: Int) : Config()

		@Serializable
		data class CreateEventConfig(val projectDef: ProjectDef) : Config()
	}

	fun showOverview()
	fun showViewEvent(eventId: Int)
	fun showCreateEvent()
}