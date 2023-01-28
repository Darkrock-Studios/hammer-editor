package com.darkrockstudios.apps.hammer.common.parcelize

import com.arkivanov.essenty.parcelable.Parceler
import kotlinx.datetime.Instant

internal expect object InstantParceler : Parceler<Instant>