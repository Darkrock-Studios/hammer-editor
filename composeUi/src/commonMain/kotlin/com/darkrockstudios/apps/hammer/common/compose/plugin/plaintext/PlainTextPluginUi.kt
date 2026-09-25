package com.darkrockstudios.apps.hammer.common.compose.plugin.plaintext

import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginUi
import com.darkrockstudios.apps.hammer.common.compose.plugin.SettingLabels
import com.darkrockstudios.apps.hammer.composeui.resources.*
import com.darkrockstudios.apps.hammer.plugins.plaintext.Italics
import com.darkrockstudios.apps.hammer.plugins.plaintext.ParagraphStyle
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextExporter
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextPlugin
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextSettingKeys
import com.darkrockstudios.apps.hammer.plugins.plaintext.SceneBreak
import org.jetbrains.compose.resources.StringResource

object PlainTextPluginUi : PluginUi {
	override val id = PlainTextPlugin.ID
	override val name = Res.string.plaintext_plugin_name

	override fun exportFormatLabels(): Map<String, StringResource> =
		mapOf(PlainTextExporter.FORMAT_ID to Res.string.plaintext_format_label)

	override fun settingLabels(): Map<String, SettingLabels> = mapOf(
		PlainTextSettingKeys.SCENE_BREAK to SettingLabels(
			label = Res.string.plaintext_scene_break_label,
			options = mapOf(
				SceneBreak.Hash.name to Res.string.plaintext_scene_break_hash,
				SceneBreak.Asterisks.name to Res.string.plaintext_scene_break_asterisks,
				SceneBreak.Blank.name to Res.string.plaintext_scene_break_blank,
			),
		),
		PlainTextSettingKeys.ITALICS to SettingLabels(
			label = Res.string.plaintext_italics_label,
			options = mapOf(
				Italics.Underscores.name to Res.string.plaintext_italics_underscores,
				Italics.Asterisks.name to Res.string.plaintext_italics_asterisks,
				Italics.Dropped.name to Res.string.plaintext_italics_dropped,
			),
		),
		PlainTextSettingKeys.PARAGRAPHS to SettingLabels(
			label = Res.string.plaintext_paragraphs_label,
			options = mapOf(
				ParagraphStyle.BlankLine.name to Res.string.plaintext_paragraphs_blank_line,
				ParagraphStyle.Indented.name to Res.string.plaintext_paragraphs_indented,
			),
		),
		PlainTextSettingKeys.CHAPTER_HEADINGS to SettingLabels(
			label = Res.string.plaintext_chapter_headings_label,
			hint = Res.string.plaintext_chapter_headings_hint,
		),
	)
}
