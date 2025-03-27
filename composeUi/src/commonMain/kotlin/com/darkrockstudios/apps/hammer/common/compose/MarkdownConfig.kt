package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.darkrockstudios.apps.hammer.common.compose.markdown.changeFontSize
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.texteditor.markdown.MarkdownConfiguration

val LocalMarkdownConfig = compositionLocalOf {
	MarkdownConfiguration.DEFAULT
}

@Composable
fun ProvideMarkdownConfig(
	isDark: Boolean,
	settings: GlobalSettings,
	content: @Composable () -> Unit
) {
	val baseMarkdownConfig =
		if (isDark) MarkdownConfiguration.DEFAULT_DARK else MarkdownConfiguration.DEFAULT
	val scaledMarkdownConfig = baseMarkdownConfig.changeFontSize(settings.editorFontSize)

	CompositionLocalProvider(
		LocalMarkdownConfig provides scaledMarkdownConfig,
		content = content
	)
}