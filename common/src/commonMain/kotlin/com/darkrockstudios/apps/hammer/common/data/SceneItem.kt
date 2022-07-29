package com.darkrockstudios.apps.hammer.common.data

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import kotlinx.serialization.Transient

@Parcelize
data class SceneItem(
    val projectDef: ProjectDef,
    val type: Type,
    val id: Int,
    val name: String,
    val order: Int,
    @Transient
    val parentPath: ScenePath,
    @Transient
    val children: List<SceneItem>? = null
) : Parcelable {
    enum class Type { Scene, Group }
}

@Parcelize
data class ScenePath(
    val pathSegments: List<SceneItem>
) : Parcelable {
    val depth: Int
        get() = pathSegments.size
}