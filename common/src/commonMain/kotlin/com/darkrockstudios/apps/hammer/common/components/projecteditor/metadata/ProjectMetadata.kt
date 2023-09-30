package com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.arkivanov.essenty.parcelable.TypeParceler
import com.darkrockstudios.apps.hammer.common.parcelize.InstantParceler
import com.darkrockstudios.apps.hammer.common.parcelize.NullableInstantParceler
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
@TypeParceler<Instant, InstantParceler>()
@TypeParceler<Instant?, NullableInstantParceler>()
data class Info(
	val created: Instant,
	val lastAccessed: Instant? = null,
	val dataVersion: Int = 0 // Default to 0, if we don't know the version, assume its the oldest
) : Parcelable