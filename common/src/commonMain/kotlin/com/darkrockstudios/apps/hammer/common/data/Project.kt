package com.darkrockstudios.apps.hammer.common.data

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.fileio.HPath

@Parcelize
data class Project(val name: String, val path: HPath) : Parcelable