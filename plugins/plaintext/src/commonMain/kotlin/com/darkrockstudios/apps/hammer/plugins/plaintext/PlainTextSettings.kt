package com.darkrockstudios.apps.hammer.plugins.plaintext

import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import kotlinx.serialization.Serializable

/** Decoded from the declared settings below, so property names are the setting keys. */
@Serializable
data class PlainTextSettings(
	val sceneBreak: SceneBreak = SceneBreak.Hash,
	val italics: Italics = Italics.Underscores,
	val paragraphs: ParagraphStyle = ParagraphStyle.BlankLine,
	val chapterHeadings: Boolean = true,
)

object PlainTextSettingKeys {
	const val SCENE_BREAK = "sceneBreak"
	const val ITALICS = "italics"
	const val PARAGRAPHS = "paragraphs"
	const val CHAPTER_HEADINGS = "chapterHeadings"
}

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

internal val plainTextSettings: List<SettingDeclaration> = PlainTextSettings().let { defaults ->
	listOf(
		SettingDeclaration.Choice(
			key = PlainTextSettingKeys.SCENE_BREAK,
			label = "Scene break",
			defaultValue = defaults.sceneBreak.name,
			options = listOf(
				SettingDeclaration.Choice.Option(SceneBreak.Hash.name, "#"),
				SettingDeclaration.Choice.Option(SceneBreak.Asterisks.name, "* * *"),
				SettingDeclaration.Choice.Option(SceneBreak.Blank.name, "Blank line"),
			),
		),
		SettingDeclaration.Choice(
			key = PlainTextSettingKeys.ITALICS,
			label = "Italics",
			defaultValue = defaults.italics.name,
			options = listOf(
				SettingDeclaration.Choice.Option(Italics.Underscores.name, "_Underscores_"),
				SettingDeclaration.Choice.Option(Italics.Asterisks.name, "*Asterisks*"),
				SettingDeclaration.Choice.Option(Italics.Dropped.name, "Removed"),
			),
		),
		SettingDeclaration.Choice(
			key = PlainTextSettingKeys.PARAGRAPHS,
			label = "Paragraphs",
			defaultValue = defaults.paragraphs.name,
			options = listOf(
				SettingDeclaration.Choice.Option(ParagraphStyle.BlankLine.name, "Blank line between"),
				SettingDeclaration.Choice.Option(ParagraphStyle.Indented.name, "Indented"),
			),
		),
		SettingDeclaration.Toggle(
			key = PlainTextSettingKeys.CHAPTER_HEADINGS,
			label = "Chapter headings",
			defaultValue = defaults.chapterHeadings,
			hint = "Only when top-level scenes are exported as chapters.",
		),
	)
}
