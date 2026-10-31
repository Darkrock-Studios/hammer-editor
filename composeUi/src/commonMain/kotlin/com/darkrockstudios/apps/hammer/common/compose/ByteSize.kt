package com.darkrockstudios.apps.hammer.common.compose

/** [bytes] in B, KB or MB, the larger units truncated to one decimal place. */
fun formatByteSize(bytes: Long): String = when {
	bytes < 1024 -> "$bytes B"
	bytes < 1024 * 1024 -> "${(bytes * 10 / 1024) / 10.0} KB"
	else -> "${(bytes * 10 / (1024 * 1024)) / 10.0} MB"
}
