package com.darkrockstudios.apps.hammer.common.compose.markdown

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.sp
import com.darkrockstudios.texteditor.markdown.MarkdownConfiguration
import io.github.aakira.napier.Napier

internal fun MarkdownConfiguration.changeFontSize(
	newBaseFontSize: Float
): MarkdownConfiguration {
	// Determine the baseline font size to adjust from
	val baselineFontSize = defaultTextStyle.fontSize.value
	if (baselineFontSize == newBaseFontSize) return this

	Napier.d("Font size OLD: $baselineFontSize NEW: $newBaseFontSize")

	val scaleFactor = newBaseFontSize / baselineFontSize

	// Create a new configuration with scaled font sizes
	// The key is to handle each property individually without relying on the copy method's handling of colors
	return MarkdownConfiguration(
		defaultTextStyle = defaultTextStyle.copy(
			fontSize = newBaseFontSize.sp
		),
		boldStyle = SpanStyle(
			fontSize = newBaseFontSize.sp,
			fontWeight = boldStyle.fontWeight,
			color = boldStyle.color,
			fontFamily = boldStyle.fontFamily,
			fontStyle = boldStyle.fontStyle,
			background = boldStyle.background
		),
		italicStyle = SpanStyle(
			fontSize = newBaseFontSize.sp,
			fontWeight = italicStyle.fontWeight,
			color = italicStyle.color,
			fontFamily = italicStyle.fontFamily,
			fontStyle = italicStyle.fontStyle,
			background = italicStyle.background
		),
		codeStyle = SpanStyle(
			fontSize = newBaseFontSize.sp,
			fontWeight = codeStyle.fontWeight,
			color = codeStyle.color,
			fontFamily = codeStyle.fontFamily,
			fontStyle = codeStyle.fontStyle,
			background = codeStyle.background
		),
		linkStyle = SpanStyle(
			fontSize = newBaseFontSize.sp,
			fontWeight = linkStyle.fontWeight,
			color = linkStyle.color,
			fontFamily = linkStyle.fontFamily,
			fontStyle = linkStyle.fontStyle,
			background = linkStyle.background
		),
		blockquoteStyle = SpanStyle(
			fontSize = newBaseFontSize.sp,
			fontWeight = blockquoteStyle.fontWeight,
			color = blockquoteStyle.color,
			fontFamily = blockquoteStyle.fontFamily,
			fontStyle = blockquoteStyle.fontStyle,
			background = blockquoteStyle.background
		),
		header1Style = SpanStyle(
			fontSize = (header1Style.fontSize.value * scaleFactor).sp,
			fontWeight = header1Style.fontWeight,
			color = header1Style.color,
			fontFamily = header1Style.fontFamily,
			fontStyle = header1Style.fontStyle,
			background = header1Style.background
		),
		header2Style = SpanStyle(
			fontSize = (header2Style.fontSize.value * scaleFactor).sp,
			fontWeight = header2Style.fontWeight,
			color = header2Style.color,
			fontFamily = header2Style.fontFamily,
			fontStyle = header2Style.fontStyle,
			background = header2Style.background
		),
		header3Style = SpanStyle(
			fontSize = (header3Style.fontSize.value * scaleFactor).sp,
			fontWeight = header3Style.fontWeight,
			color = header3Style.color,
			fontFamily = header3Style.fontFamily,
			fontStyle = header3Style.fontStyle,
			background = header3Style.background
		),
		header4Style = SpanStyle(
			fontSize = (header4Style.fontSize.value * scaleFactor).sp,
			fontWeight = header4Style.fontWeight,
			color = header4Style.color,
			fontFamily = header4Style.fontFamily,
			fontStyle = header4Style.fontStyle,
			background = header4Style.background
		),
		header5Style = SpanStyle(
			fontSize = (header5Style.fontSize.value * scaleFactor).sp,
			fontWeight = header5Style.fontWeight,
			color = header5Style.color,
			fontFamily = header5Style.fontFamily,
			fontStyle = header5Style.fontStyle,
			background = header5Style.background
		),
		header6Style = SpanStyle(
			fontSize = (header6Style.fontSize.value * scaleFactor).sp,
			fontWeight = header6Style.fontWeight,
			color = header6Style.color,
			fontFamily = header6Style.fontFamily,
			fontStyle = header6Style.fontStyle,
			background = header6Style.background
		)
	)
}