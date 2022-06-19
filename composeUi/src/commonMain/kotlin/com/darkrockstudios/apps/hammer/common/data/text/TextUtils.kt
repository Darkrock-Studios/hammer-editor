package com.darkrockstudios.apps.hammer.common.data.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.aakira.napier.Napier

fun AnnotatedString.toMarkdown(): String {
    var markdownText: String = text
    spanStyles.forEach { range ->
        when {
            isBoldStyle(range.item) -> {
                val affectedText = text.subSequence(range.start, range.end)

                val annotation = styleAnnotations[TextStyle.Bold]
                val annotatedText = "$annotation$affectedText$annotation"

                markdownText = markdownText.replaceRange(range.start, range.end, annotatedText)
            }
            isItalicStyle(range.item) -> {

            }
            else -> {
                Napier.w { "Unhandled Span Style @ ${range.start}-${range.end}" }
            }
        }
    }

    return markdownText
}

private fun isBoldStyle(style: SpanStyle): Boolean = style.fontWeight == FontWeight.Bold
private fun isItalicStyle(style: SpanStyle): Boolean = style.fontStyle == FontStyle.Italic

fun String.markdownToAnnotatedString(): AnnotatedString {

    val result = parseMarkdown(this)
    val builder = AnnotatedString.Builder(result.copy)

    for (entity in result.entities) {
        when (entity) {
            is Entity.Bold -> builder.addBoldStyle(entity.start, entity.end)
            is Entity.Italic -> builder.addItalicsStyle(entity.start, entity.end)
            is Entity.Underline -> builder.addUnderlineStyle(entity.start, entity.end)
            is Entity.StrikeThrough -> builder.addStrikeThroughStyle(entity.start, entity.end)
            //is Entity.Hyperlink -> sb.setHyperlinkSpan(entity.start, entity.end, entity.url)
            else -> {
                Napier.w { "Unhandled markdown style: $entity" }
            }
        }
    }

    return builder.toAnnotatedString()
}

internal fun AnnotatedString.Builder.addBoldStyle(start: Int, end: Int) {
    addStyle(boldStyle(), start, end)
}

internal fun boldStyle() = SpanStyle(fontWeight = FontWeight.Bold)

internal fun AnnotatedString.Builder.addItalicsStyle(start: Int, end: Int) {
    addStyle(italicsStyle(), start, end)
}

internal fun italicsStyle() = SpanStyle(fontStyle = FontStyle.Italic)

internal fun AnnotatedString.Builder.addUnderlineStyle(start: Int, end: Int) {
    addStyle(underlineStyle(), start, end)
}

internal fun underlineStyle() = SpanStyle(textDecoration = TextDecoration.Underline)

internal fun AnnotatedString.Builder.addStrikeThroughStyle(start: Int, end: Int) {
    addStyle(strikeThroughStyle(), start, end)
}

internal fun strikeThroughStyle() = SpanStyle(textDecoration = TextDecoration.LineThrough)

private enum class TextStyle {
    Bold,
    Italics,
    Underline,
    StrikeThrough
}

private val styleAnnotations = mapOf(
    TextStyle.Bold to "**",
    TextStyle.Italics to "_",
    TextStyle.Underline to "__",
    TextStyle.StrikeThrough to "~~"
)
