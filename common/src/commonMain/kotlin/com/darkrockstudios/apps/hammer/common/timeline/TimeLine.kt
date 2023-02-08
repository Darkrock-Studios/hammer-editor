package com.darkrockstudios.apps.hammer.common.timeline

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.projectroot.Router

interface TimeLine : Router, HammerComponent {

	val stack: Value<ChildStack<Config, Destination>>

	sealed class Destination {
		data class TimeLineOverviewDestination(val component: TimeLineOverview) : Destination()

		data class ViewEventDestination(val component: ViewTimeLineEvent) : Destination()

		data class CreateEventDestination(val component: CreateTimeLineEvent) : Destination()
	}

	sealed class Config : Parcelable {
		@Parcelize
		data class TimeLineOverviewConfig(val projectDef: ProjectDef) : Config()

		@Parcelize
		data class ViewEventConfig(val projectDef: ProjectDef, val eventId: Int) : Config()

		@Parcelize
		data class CreateEventConfig(val projectDef: ProjectDef) : Config()
	}

	fun showOverview()
	fun showViewEvent(eventId: Int)
	fun showCreateEvent()
}