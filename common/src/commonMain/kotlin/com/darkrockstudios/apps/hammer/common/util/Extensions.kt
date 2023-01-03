package com.darkrockstudios.apps.hammer.common.util

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.time.Duration

fun Int.numDigits() = when (this) {
	0 -> 1
	else -> log10(abs(toDouble())).toInt() + 1
}

fun <T> Flow<T>.debounceUntilQuiescent(duration: Duration): Flow<T> = channelFlow {
	var job: Job? = null
	collect { value ->
		job?.cancel()
		job = launch {
			delay(duration)
			send(value)
			job = null
		}
	}
}