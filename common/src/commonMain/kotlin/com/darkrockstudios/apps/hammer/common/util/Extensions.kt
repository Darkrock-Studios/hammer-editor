package com.darkrockstudios.apps.hammer.common.util

import kotlin.math.abs
import kotlin.math.log10

fun Int.numDigits() = when (this) {
    0 -> 1
    else -> log10(abs(toDouble())).toInt() + 1
}