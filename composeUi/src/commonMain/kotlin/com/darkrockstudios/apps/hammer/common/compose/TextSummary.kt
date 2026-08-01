package com.darkrockstudios.apps.hammer.common.compose

fun String.firstNonBlankLine(): String =
	lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

fun String.truncateWithEllipsis(limit: Int): String =
	if (length > limit) take(limit - 1).trimEnd() + "…" else this
