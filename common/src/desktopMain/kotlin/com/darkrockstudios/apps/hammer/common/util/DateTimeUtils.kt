package com.darkrockstudios.apps.hammer.common.util

import kotlinx.datetime.*
import java.time.format.DateTimeFormatter

actual fun Instant.formatLocal(format: String): String =
    toLocalDateTime(TimeZone.currentSystemDefault()).format(format)

actual fun LocalDateTime.format(format: String): String =
    DateTimeFormatter.ofPattern(format).format(this.toJavaLocalDateTime())