package com.darkrockstudios.apps.hammer.common.parcelize

import com.arkivanov.essenty.parcelable.Parceler
import dev.icerock.moko.resources.StringResource
import kotlinx.datetime.Instant

internal actual object InstantParceler : Parceler<Instant>

// TODO can remove this once the Parceler bug is fixed
internal actual object NullableInstantParceler : Parceler<Instant?>

internal actual object StringResourceParceler : Parceler<StringResource?>