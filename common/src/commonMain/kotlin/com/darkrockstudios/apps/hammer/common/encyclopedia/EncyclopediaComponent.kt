package com.darkrockstudios.apps.hammer.common.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.projectInject

class EncyclopediaComponent(
	componentContext: ComponentContext,
	private val projectDef: ProjectDef
) : ProjectComponentBase(projectDef, componentContext), Encyclopedia {
	private val _state = MutableValue(Encyclopedia.State(projectDef = projectDef))
	override val state: Value<Encyclopedia.State> = _state

	private val encyclopediaRepository: EncyclopediaRepository by projectInject()
	
}