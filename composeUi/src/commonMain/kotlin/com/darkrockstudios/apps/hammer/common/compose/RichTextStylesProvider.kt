package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.texteditor.RichTextStyles

/**
 * Heading sizes are relative to the editor's text style, so the body size lives in
 * [LocalEditorTextStyle] alone and changing it rewrites no document.
 */
val HammerRichTextStyles = RichTextStyles(
	defaultTextStyle = SpanStyle(),
	header1Style = SpanStyle(fontSize = 2.em, fontWeight = FontWeight.Bold),
	header2Style = SpanStyle(fontSize = 1.5.em, fontWeight = FontWeight.Bold),
	header3Style = SpanStyle(fontSize = 1.17.em, fontWeight = FontWeight.Bold),
	header4Style = SpanStyle(fontSize = 1.em, fontWeight = FontWeight.Bold),
	header5Style = SpanStyle(fontSize = 0.83.em, fontWeight = FontWeight.Bold),
	header6Style = SpanStyle(fontSize = 0.75.em, fontWeight = FontWeight.Bold),
)

val HammerRichTextStylesDark = HammerRichTextStyles.copy(
	codeStyle = RichTextStyles.DEFAULT_DARK.codeStyle,
	highlightStyle = RichTextStyles.DEFAULT_DARK.highlightStyle,
)

val LocalRichTextStyles = compositionLocalOf { HammerRichTextStyles }

/** The editor's body text style; every rich text size scales from it. */
val LocalEditorTextStyle = compositionLocalOf {
	TextStyle(fontSize = GlobalSettings.DEFAULT_FONT_SIZE.sp)
}

@Composable
fun ProvideRichTextStyles(
	isDark: Boolean,
	settings: GlobalSettings,
	content: @Composable () -> Unit
) {
	CompositionLocalProvider(
		LocalRichTextStyles provides if (isDark) HammerRichTextStylesDark else HammerRichTextStyles,
		LocalEditorTextStyle provides TextStyle(fontSize = settings.editorFontSize.sp),
		content = content
	)
}

/** A first-line indent that scales with the body size. */
fun TextStyle.withParagraphIndent(): TextStyle = copy(textIndent = TextIndent(firstLine = 1.5.em))
