package com.darkrockstudios.apps.hammer.common.data.projectstatistics

private val whitespaceRun = Regex("""\s+""")

fun countWords(text: String): Int {
	val trimmed = text.trim()
	return if (trimmed.isEmpty()) 0 else trimmed.split(whitespaceRun).size
}
