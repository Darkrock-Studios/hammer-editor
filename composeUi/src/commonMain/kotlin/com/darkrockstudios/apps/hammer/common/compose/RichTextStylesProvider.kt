package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.darkrockstudios.apps.hammer.common.compose.markdown.changeFontSize
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.texteditor.RichTextStyles

val LocalRichTextStyles = compositionLocalOf {
	RichTextStyles.DEFAULT
}

@Composable
fun ProvideRichTextStyles(
	isDark: Boolean,
	settings: GlobalSettings,
	content: @Composable () -> Unit
) {
	val baseStyles = if (isDark) RichTextStyles.DEFAULT_DARK else RichTextStyles.DEFAULT
	val scaledStyles = baseStyles.changeFontSize(settings.editorFontSize)

	CompositionLocalProvider(
		LocalRichTextStyles provides scaledStyles,
		content = content
	)
}
