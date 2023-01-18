package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

@Parcelize
data class EntryDef(
	val projectDef: ProjectDef,
	val id: Int,
	val type: EntryType,
	val name: String
) : Parcelable