package com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.arkivanov.essenty.parcelable.TypeParceler
import com.darkrockstudios.apps.hammer.common.parcelize.InstantParceler
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class ProjectMetadata(
	val info: Info
) : Parcelable {
	companion object {
		const val FILENAME = "project.toml"
	}
}

@Parcelize
@Serializable
data class Info(
	@TypeParceler<Instant, InstantParceler>() val created: Instant
) : Parcelable