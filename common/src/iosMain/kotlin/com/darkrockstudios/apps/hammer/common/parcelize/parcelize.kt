package com.darkrockstudios.apps.hammer.common.parcelize

import com.arkivanov.essenty.parcelable.Parceler
import dev.icerock.moko.resources.StringResource
import kotlinx.datetime.Instant

internal actual object InstantParceler : Parceler<Instant>

internal actual object StringResourceParceler : Parceler<StringResource>