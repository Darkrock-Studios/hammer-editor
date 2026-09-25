package com.darkrockstudios.apps.hammer.common.compose.plugin.plaintext

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDropdown
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginUi
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.composeui.resources.*
import com.darkrockstudios.apps.hammer.plugins.plaintext.Italics
import com.darkrockstudios.apps.hammer.plugins.plaintext.ParagraphStyle
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextExporter
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextPlugin
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextSettings
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextSettingsStore
import com.darkrockstudios.apps.hammer.plugins.plaintext.SceneBreak
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.koinInject

object PlainTextPluginUi : PluginUi {
	override val id = PlainTextPlugin.ID
	override val name = Res.string.plaintext_plugin_name

	override fun exportFormatLabels(): Map<String, StringResource> =
		mapOf(PlainTextExporter.FORMAT_ID to Res.string.plaintext_format_label)

	override val settingsPane: @Composable ColumnScope.() -> Unit = {
		val store = koinInject<PlainTextSettingsStore>()
		val settings by store.settings.collectAsState()
		val scope = rememberCoroutineScope()
		PlainTextSettingsContent(
			settings = settings,
			onChange = { transform -> scope.launch { store.update(transform) } },
		)
	}
}

@Composable
internal fun ColumnScope.PlainTextSettingsContent(
	settings: PlainTextSettings,
	onChange: ((PlainTextSettings) -> PlainTextSettings) -> Unit,
) {
	HdHairlineDropdown(
		title = Res.string.plaintext_scene_break_label.get(),
		options = SceneBreak.entries,
		selected = settings.sceneBreak,
		onSelect = { value -> onChange { it.copy(sceneBreak = value) } },
		label = { sceneBreakLabel(it).get() },
	)
	HdHairlineDropdown(
		title = Res.string.plaintext_italics_label.get(),
		options = Italics.entries,
		selected = settings.italics,
		onSelect = { value -> onChange { it.copy(italics = value) } },
		label = { italicsLabel(it).get() },
	)
	HdHairlineDropdown(
		title = Res.string.plaintext_paragraphs_label.get(),
		options = ParagraphStyle.entries,
		selected = settings.paragraphs,
		onSelect = { value -> onChange { it.copy(paragraphs = value) } },
		label = { paragraphsLabel(it).get() },
	)
	HdHairlineToggleRow(
		checked = settings.chapterHeadings,
		onCheckedChange = { value -> onChange { it.copy(chapterHeadings = value) } },
		label = Res.string.plaintext_chapter_headings_label.get(),
		hint = Res.string.plaintext_chapter_headings_hint.get(),
	)
}

private fun sceneBreakLabel(value: SceneBreak): StringResource = when (value) {
	SceneBreak.Hash -> Res.string.plaintext_scene_break_hash
	SceneBreak.Asterisks -> Res.string.plaintext_scene_break_asterisks
	SceneBreak.Blank -> Res.string.plaintext_scene_break_blank
}

private fun italicsLabel(value: Italics): StringResource = when (value) {
	Italics.Underscores -> Res.string.plaintext_italics_underscores
	Italics.Asterisks -> Res.string.plaintext_italics_asterisks
	Italics.Dropped -> Res.string.plaintext_italics_dropped
}

private fun paragraphsLabel(value: ParagraphStyle): StringResource = when (value) {
	ParagraphStyle.BlankLine -> Res.string.plaintext_paragraphs_blank_line
	ParagraphStyle.Indented -> Res.string.plaintext_paragraphs_indented
}
