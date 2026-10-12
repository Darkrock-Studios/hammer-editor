package com.darkrockstudios.apps.hammer.plugins.plaintext

import kotlinx.serialization.Serializable

@Serializable
data class PlainTextSettings(
	val sceneBreak: SceneBreak = SceneBreak.Hash,
	val italics: Italics = Italics.Underscores,
	val paragraphs: ParagraphStyle = ParagraphStyle.BlankLine,
	val chapterHeadings: Boolean = true,
)

enum class SceneBreak(val marker: String) {
	Hash("#"),
	Asterisks("* * *"),

	/** An empty line on top of the usual paragraph spacing. */
	Blank(""),
}

enum class Italics {
	Underscores,
	Asterisks,
	Dropped,
}

enum class ParagraphStyle {
	BlankLine,
	Indented,
}
