package com.darkrockstudios.apps.hammer.common.data

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize

@Parcelize
data class SceneDefinition(
    val projectDef: ProjectDef,
    val id: Int,
    val name: String,
    val order: Int,
) : Parcelable

typealias SceneDef = SceneDefinition