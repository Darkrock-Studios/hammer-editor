package com.darkrockstudios.apps.hammer.common.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime

expect fun Instant.formatLocal(format: String): String
expect fun LocalDateTime.format(format: String): String