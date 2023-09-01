package com.darkrockstudios.apps.hammer.common.parcelize

import com.arkivanov.essenty.parcelable.Parceler
import dev.icerock.moko.resources.StringResource
import kotlinx.datetime.Instant

internal expect object InstantParceler : Parceler<Instant>
internal expect object NullableInstantParceler : Parceler<Instant?>

// TODO change back to non-nullable StringResource when Essenty is fixed
internal expect object StringResourceParceler : Parceler<StringResource?>