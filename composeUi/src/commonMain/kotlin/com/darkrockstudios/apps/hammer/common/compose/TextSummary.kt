package com.darkrockstudios.apps.hammer.common.compose

import com.darkrockstudios.apps.hammer.common.data.search.markdownTitleLine

/** Every caller summarizes stored Markdown, so the line is flattened to the prose behind it. */
fun String.firstNonBlankLine(): String = markdownTitleLine(this)

fun String.truncateWithEllipsis(limit: Int): String =
	if (length > limit) take(limit - 1).trimEnd() + "…" else this
