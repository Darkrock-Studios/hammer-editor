package com.darkrockstudios.apps.hammer.common.data.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.darkrockstudios.richtexteditor.model.Style
import com.darkrockstudios.richtexteditor.utils.RichTextValueSnapshot
import io.github.aakira.napier.Napier

private fun doOverlap(
	a: AnnotatedString.Range<SpanStyle>,
	b: AnnotatedString.Range<SpanStyle>
): Boolean {
	return if (a.start <= b.start) {
		b.start <= a.end
	} else {
		a.start <= b.end
	}
}

private fun AnnotatedString.Range<SpanStyle>.overlaps(
	that: AnnotatedString.Range<SpanStyle>
): Boolean {
	return doOverlap(this, that)
}

private fun doOverlap(
	a: RichTextValueSnapshot.RichTextValueSpanSnapshot,
	b: RichTextValueSnapshot.RichTextValueSpanSnapshot
): Boolean {
	return if (a.start <= b.start) {
		b.start <= a.end
	} else {
		a.start <= b.end
	}
}

private fun RichTextValueSnapshot.RichTextValueSpanSnapshot.overlaps(
	that: RichTextValueSnapshot.RichTextValueSpanSnapshot
): Boolean {
	return doOverlap(this, that)
}

fun RichTextValueSnapshot.toMarkdown(): String {
	val finalStyles = mutableListOf<RichTextValueSnapshot.RichTextValueSpanSnapshot>()
	val styles = spanStyles.toMutableList()
	val it = spanStyles.iterator()
	// Remove overlapping styles
	while (it.hasNext()) {
		val outer = it.next()
		for (inner in spanStyles) {
			if (outer != inner && outer.overlaps(inner)) {
				styles.remove(inner)
			}
		}

		finalStyles.add(outer)
	}
	finalStyles.sortBy { it.start }

	var stringBuilder = ""
	var mark = 0
	finalStyles.forEach { range ->
		stringBuilder += text.subSequence(mark, range.start)
		mark = range.end

		when {
			isBoldTag(range.tag) -> {
				stringBuilder += stylizeText(text, range, TextStyle.Bold)
			}

			isItalicTag(range.tag) -> {
				stringBuilder += stylizeText(text, range, TextStyle.Italics)
			}

			else -> {
				Napier.w { "Unhandled Span Style @ ${range.start}-${range.end}" }
			}
		}
	}

	if (mark < text.length) {
		stringBuilder += text.subSequence(mark, text.length)
	}

	return stringBuilder
}

private fun stylizeText(
	text: String,
	range: RichTextValueSnapshot.RichTextValueSpanSnapshot,
	style: TextStyle
): String {
	val affectedText = text.subSequence(range.start, range.end)
	val annotation = styleAnnotations[style]
	return "$annotation$affectedText$annotation"
}

private fun isBoldStyle(style: SpanStyle): Boolean = style.fontWeight == FontWeight.Bold
private fun isItalicStyle(style: SpanStyle): Boolean = style.fontStyle == FontStyle.Italic

private fun isBoldTag(tag: String): Boolean =
	tag.startsWith("${Style.Bold::class.simpleName}/")

private fun isItalicTag(tag: String): Boolean =
	tag.startsWith("${Style.Italic::class.simpleName}/")

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

fun String.markdownToSnapshot(): RichTextValueSnapshot {
	val annotatedString = markdownToAnnotatedString()
	return fromAnnotatedString(annotatedString)
}

private fun fromAnnotatedString(
	annotatedString: AnnotatedString
) = RichTextValueSnapshot(
	text = annotatedString.text,
	spanStyles = annotatedString.spanStyles.map {
		it.toRichTextValueSpanSnapshot()
	},
	paragraphStyles = annotatedString.paragraphStyles.map {
		it.toRichTextValueSpanSnapshot()
	},
	selectionPosition = 0
)

private fun tagFromStyle(style: SpanStyle?): String {
	return if (style == null) {
		Napier.w("tagFromStyle: Null style")
		""
	} else if (isBoldStyle(style)) {
		"${Style.Bold::class.simpleName}/"
	} else if (isItalicStyle(style)) {
		"${Style.Italic::class.simpleName}/"
	} else {
		Napier.w("tagFromStyle: Unhandled Style: ${style::class.simpleName}")
		""
	}
}

private fun <T> AnnotatedString.Range<T>.toRichTextValueSpanSnapshot(): RichTextValueSnapshot.RichTextValueSpanSnapshot {
	val rtvTag = tagFromStyle(item as? SpanStyle)
	return RichTextValueSnapshot.RichTextValueSpanSnapshot(start = start, end = end, tag = rtvTag)
}