package com.darkrockstudios.apps.hammer.common.compose

import com.darkrockstudios.apps.hammer.common.util.format
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The app's plain date stamp, in local time: "3 Nov 2026". */
fun Instant.stampDate(): String =
	toLocalDateTime(TimeZone.currentSystemDefault()).format("d MMM yyyy")

/** [stampDate] with the local time: "3 Nov 2026, 21:40". */
fun Instant.stampDateTime(): String =
	toLocalDateTime(TimeZone.currentSystemDefault()).format("d MMM yyyy, HH:mm")
