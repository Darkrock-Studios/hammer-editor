package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.runtime.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.darkrockstudios.apps.hammer.base.diff.DiffResult
import com.darkrockstudios.apps.hammer.base.diff.DiffSpan
import com.darkrockstudios.apps.hammer.base.diff.PreparedText
import com.darkrockstudios.apps.hammer.base.diff.ProseDiff
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Idle delay before recomputing a content diff while a local field is being edited. */
internal const val CONTENT_DIFF_DELAY_MS = 400L

/**
 * Word-level diff between an immutable [serverContent] and an editable [localContent], recomputed
 * off the main thread after a short idle. The server side is prepared (markdown-stripped and
 * tokenized) once and reused, so only the edited side is reprocessed on each keystroke.
 *
 * Returns null until the server side is prepared and the first diff completes.
 */
@Composable
internal fun rememberContentDiff(serverContent: String, localContent: String): DiffResult? {
	var preparedServer by remember(serverContent) { mutableStateOf<PreparedText?>(null) }
	LaunchedEffect(serverContent) {
		preparedServer = withContext(Dispatchers.Default) { ProseDiff.prepare(serverContent) }
	}

	var diffResult by remember(serverContent) { mutableStateOf<DiffResult?>(null) }
	LaunchedEffect(preparedServer, localContent) {
		val prepared = preparedServer ?: return@LaunchedEffect
		delay(CONTENT_DIFF_DELAY_MS)
		diffResult = withContext(Dispatchers.Default) {
			ProseDiff.diff(prepared, ProseDiff.prepare(localContent))
		}
	}
	return diffResult
}

/** Background alpha for conflict diff highlights — light enough to read text through. */
private const val CONFLICT_DIFF_HIGHLIGHT_ALPHA = 0.28f

/** Red strikethrough for removed text, shown on the read-only remote side. */
@Composable
internal fun diffDeletedStyle(): SpanStyle {
	val danger = LocalHammerColors.current.danger
	return remember(danger) {
		SpanStyle(
			background = danger.copy(alpha = CONFLICT_DIFF_HIGHLIGHT_ALPHA),
			textDecoration = TextDecoration.LineThrough,
		)
	}
}

/** Green underline for added text, shown on the editable local side. */
@Composable
internal fun diffInsertedStyle(): SpanStyle {
	val success = LocalHammerColors.current.success
	return remember(success) {
		SpanStyle(
			background = success.copy(alpha = CONFLICT_DIFF_HIGHLIGHT_ALPHA),
			textDecoration = TextDecoration.Underline,
		)
	}
}

/**
 * Overlay [style] onto [text] across the given diff [spans], leaving the text itself unchanged.
 *
 * Used for the read-only side of a conflict where the content is plain text. Spans whose range
 * falls outside the current text are skipped — which can happen briefly while the editable side
 * is being typed into and the diff hasn't been recomputed yet.
 */
internal fun diffHighlightedString(
	text: String,
	spans: List<DiffSpan>,
	style: SpanStyle,
): AnnotatedString {
	if (spans.isEmpty()) return AnnotatedString(text)
	return buildAnnotatedString {
		append(text)
		applyDiffSpans(spans, style, text.length)
	}
}

/**
 * A [VisualTransformation] that paints diff [spans] over an editable field's text without
 * altering its length, so the cursor and offset mapping stay identity.
 */
internal class DiffHighlightTransformation(
	private val spans: List<DiffSpan>,
	private val style: SpanStyle,
) : VisualTransformation {
	override fun filter(text: AnnotatedString): TransformedText {
		if (spans.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
		val annotated = buildAnnotatedString {
			append(text)
			applyDiffSpans(spans, style, text.length)
		}
		return TransformedText(annotated, OffsetMapping.Identity)
	}

	override fun equals(other: Any?): Boolean =
		other is DiffHighlightTransformation && other.spans == spans && other.style == style

	override fun hashCode(): Int = 31 * spans.hashCode() + style.hashCode()
}

private fun AnnotatedString.Builder.applyDiffSpans(
	spans: List<DiffSpan>,
	style: SpanStyle,
	length: Int,
) {
	for (span in spans) {
		val start = span.range.start
		val end = span.range.endExclusive
		if (start in 0..length && end in start..length) {
			addStyle(style, start, end)
		}
	}
}
