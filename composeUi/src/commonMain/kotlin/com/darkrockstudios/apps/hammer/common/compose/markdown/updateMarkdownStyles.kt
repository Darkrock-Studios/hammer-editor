package com.darkrockstudios.apps.hammer.common.compose.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.darkrockstudios.texteditor.markdown.MarkdownConfiguration
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.state.TextEditorState

internal fun MarkdownExtension.updateMarkdownConfiguration(newConfig: MarkdownConfiguration) {
	val oldConfig = markdownConfiguration
	updateMarkdownStyles(
		state = editorState,
		oldConfig = oldConfig,
		newConfig = newConfig
	)
	markdownConfiguration = newConfig
}

/**
 * Updates all text in the editor to use the new markdown configuration styles.
 * This preserves the semantic meaning of styles while updating their visual appearance.
 *
 * @param state The TextEditorState to update
 * @param oldConfig The previous configuration that was used
 * @param newConfig The new configuration to apply
 */
internal fun updateMarkdownStyles(
	state: TextEditorState,
	oldConfig: MarkdownConfiguration,
	newConfig: MarkdownConfiguration
) {
	if (state.textLines.isEmpty()) return

	val styleMapping = mapOf(
		oldConfig.defaultTextStyle to newConfig.defaultTextStyle,
		oldConfig.boldStyle to newConfig.boldStyle,
		oldConfig.italicStyle to newConfig.italicStyle,
		oldConfig.codeStyle to newConfig.codeStyle,
		oldConfig.linkStyle to newConfig.linkStyle,
		oldConfig.blockquoteStyle to newConfig.blockquoteStyle,
		oldConfig.header1Style to newConfig.header1Style,
		oldConfig.header2Style to newConfig.header2Style,
		oldConfig.header3Style to newConfig.header3Style,
		oldConfig.header4Style to newConfig.header4Style,
		oldConfig.header5Style to newConfig.header5Style,
		oldConfig.header6Style to newConfig.header6Style
	)

	state.processLines { _: Int, line: AnnotatedString ->
		buildAnnotatedString {
			append(line.text)
			if (line.spanStyles.isEmpty()) {
				addStyle(newConfig.defaultTextStyle, 0, line.length)
			} else {
				line.spanStyles.forEach { span ->
					val newStyle = findMatchingStyle(span.item, styleMapping)
					if (newStyle != null) {
						addStyle(newStyle, span.start, span.end)
					} else {
						// Keep the original style if it's not a markdown style
						println("Skipping style update")
						addStyle(span.item, span.start, span.end)
					}
				}
			}
		}
	}
}

/**
 * Find a matching style in the style mapping.
 */
private fun findMatchingStyle(
	style: SpanStyle,
	styleMapping: Map<SpanStyle, SpanStyle>
): SpanStyle? {
	for ((oldStyle, newStyle) in styleMapping) {
		if (deepCompareSpanStyles(oldStyle, style)) {
			return newStyle
		}
	}
	return null
}

private fun deepCompareSpanStyles(style1: SpanStyle, style2: SpanStyle): Boolean {
	if (style1 === style2) return true

	if (style1.fontWeight != style2.fontWeight) return false
	if (style1.fontStyle != style2.fontStyle) return false
	if (style1.fontFamily != style2.fontFamily) return false
	if (style1.textDecoration != style2.textDecoration) return false
	if (style1.fontSize != style2.fontSize) return false
	if (style1.color != style2.color) return false
	if (style1.background != style2.background) return false
	if (style1.letterSpacing != style2.letterSpacing) return false
	if (style1.shadow != style2.shadow) return false
	if (style1.baselineShift != style2.baselineShift) return false
	if (style1.textGeometricTransform != style2.textGeometricTransform) return false
	if (style1.localeList != style2.localeList) return false
	return true
}