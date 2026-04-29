package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface ProjectSettings : HammerComponent {
	val spellCheckSettings: SpellCheckSettings
	val projectInfoState: Value<ProjectInfoState>

	fun setAuthorName(name: String?)
	fun setTheme(theme: ProjectTheme?)
	fun setWordCountGoal(goal: WordCountGoal?)

	data class ProjectInfoState(
		val data: ProjectData = ProjectData(),
		val isLoaded: Boolean = false,
	)
}