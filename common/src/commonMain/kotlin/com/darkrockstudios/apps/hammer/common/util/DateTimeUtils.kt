package com.darkrockstudios.apps.hammer.common.util

import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

expect fun Instant.formatLocal(format: String): String
expect fun LocalDateTime.format(format: String): String