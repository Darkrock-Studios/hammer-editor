package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** [current], or the last one there was, so a dialog keeps its content while it animates closed. */
@Composable
fun <T : Any> lastOf(current: T?): T? {
	val last = remember { LastValue<T>() }
	if (current != null) last.value = current
	return current ?: last.value
}

private class LastValue<T : Any> {
	var value: T? = null
}
