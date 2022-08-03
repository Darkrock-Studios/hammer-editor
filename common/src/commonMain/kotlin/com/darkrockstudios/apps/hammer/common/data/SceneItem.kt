package com.darkrockstudios.apps.hammer.common.data

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize

@Parcelize
data class SceneItem(
    val projectDef: ProjectDef,
    val type: Type,
    val id: Int,
    val name: String,
    val order: Int,
) : Parcelable {
    enum class Type { Scene, Group, Root }

    val isRootScene: Boolean
        get() = type == Type.Root

    override fun toString(): String {
        val idStr = id.toString().padStart(3, '0')
        return "$idStr - $type - $order - $name"
    }
}

@Parcelize
data class ScenePathSegments(
    val pathSegments: List<Int>
) : Parcelable {
    val depth: Int
        get() = pathSegments.size
}