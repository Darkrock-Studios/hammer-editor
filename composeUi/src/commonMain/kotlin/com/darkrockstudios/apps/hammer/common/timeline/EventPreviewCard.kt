package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.common.components.timeline.EventPreview
import com.darkrockstudios.apps.hammer.common.compose.LocalImageProvider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdReferenceChip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdGlyphColor
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdMark
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdSwatchColor
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberStoryDateWords
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceSyntax
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.formatStoryDateRange
import com.darkrockstudios.apps.hammer.timeline_selection_more
import com.darkrockstudios.apps.hammer.timeline_subject_archived
import com.darkrockstudios.apps.hammer.timeline_subject_missing
import com.darkrockstudios.texteditor.richstyle.ImageBlockResource

const val EVENT_PREVIEW_CARD_TAG = "Timeline Event Preview"

internal const val SUMMARY_CHIPS = 6
internal const val SUMMARY_CHIPS_PHONE = 4
private const val SUMMARY_CHIP_LINES = 2
private const val SUMMARY_CHIP_NAME_CHARS = 12
private const val COMPACT_EXCERPT_LINES = 3
private const val STRIP_EXCERPT_LINES = 2
private const val COMPACT_REFERENCES = 3

private val ThumbnailSize = 56.dp

internal fun String.ellipsize(max: Int): String = if (length <= max) this else take(max - 1).trimEnd() + "…"

/**
 * An event as its hover card ([compact]) and the selection strip show it:
 *
 *     ┌──────┐ The Siege of Hollowmere
 *     │      │ YEAR 1 – YEAR 4 · KEEP
 *     └──────┘ The walls held for three winters…
 *              ☉ Alice, ◇ Hollowmere, +2
 *
 * The thumbnail is a placed entry's primary image, left out when it has none. [compact] sets the
 * references as one line of text; otherwise they are up to [maxChips] chips that open through
 * [onReference], and the excerpt is held to fewer lines.
 */
@Composable
fun EventPreviewCard(
	preview: EventPreview,
	compact: Boolean,
	modifier: Modifier = Modifier,
	maxChips: Int = SUMMARY_CHIPS,
	onReference: (CatalogItem) -> Unit = {},
) {
	val words = rememberStoryDateWords()
	val start = preview.start
	val whenText = if (start != null) {
		formatStoryDateRange(preview.calendar ?: CalendarDef(), start, preview.end, words)
	} else {
		preview.whenText.ifBlank { null }
	}
	val subjectNote = when (preview.subjectState) {
		EventPreview.SubjectState.Present -> null
		EventPreview.SubjectState.Missing -> Res.string.timeline_subject_missing.get()
		EventPreview.SubjectState.Archived -> Res.string.timeline_subject_archived.get()
	}
	val meta = listOfNotNull(whenText, preview.lane, subjectNote).joinToString(" · ")

	Row(
		modifier = modifier.testTag(EVENT_PREVIEW_CARD_TAG),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalAlignment = Alignment.Top,
	) {
		preview.imageId?.let { EventThumbnail(imageId = it, size = ThumbnailSize) }
		Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
			Text(
				text = preview.title,
				style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
				fontWeight = if (compact) FontWeight.Bold else null,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
			if (meta.isNotEmpty()) {
				HdMonoLabel(text = meta, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
			}
			preview.excerpt?.let { excerpt ->
				Text(
					text = excerpt,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = if (compact) COMPACT_EXCERPT_LINES else STRIP_EXCERPT_LINES,
					overflow = TextOverflow.Ellipsis,
				)
			}
			if (preview.references.isNotEmpty()) {
				if (compact) {
					ReferenceLine(preview.references)
				} else {
					SummaryChips(references = preview.references, max = maxChips, onClick = onReference)
				}
			}
		}
	}
}

/**
 * A project image by id, square and cropped, through the editor's image provider. Holds its place
 * while loading and draws nothing once the image proves missing.
 */
@Composable
private fun EventThumbnail(imageId: Int, size: Dp) {
	val provider = LocalImageProvider.current ?: return
	val resource by remember(provider, imageId) { provider.resolve(ImageReferenceSyntax.reference(imageId)) }
	val frame = Modifier
		.size(size)
		.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, RectangleShape)
	when (val loaded = resource) {
		is ImageBlockResource.Loaded -> Image(
			bitmap = loaded.bitmap,
			contentDescription = null,
			contentScale = ContentScale.Crop,
			modifier = frame,
		)

		ImageBlockResource.Loading -> Box(modifier = frame.background(MaterialTheme.colorScheme.surfaceContainerHigh))
		is ImageBlockResource.Failed -> Unit
	}
}

/** `☉ Alice, ◇ Wonderland, +2` on one line: the first few references, then a count of the rest. */
@Composable
private fun ReferenceLine(references: List<CatalogItem>) {
	val shown = references.take(COMPACT_REFERENCES)
	val glyphColors = shown.map { it.hdSwatchColor() }
	val hidden = references.size - shown.size
	val more = if (hidden > 0) Res.string.timeline_selection_more.get(hidden) else null
	val text = buildAnnotatedString {
		shown.forEachIndexed { i, item ->
			if (i > 0) append(", ")
			withStyle(SpanStyle(color = glyphColors[i])) { append(item.hdMark()) }
			append(' ')
			append(item.name)
		}
		if (more != null) {
			append(", ")
			append(more)
		}
	}
	Text(
		text = text,
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurface,
		maxLines = 1,
		overflow = TextOverflow.Ellipsis,
	)
}

/**
 * Up to [max] reference chips, glyph and name, on at most [SUMMARY_CHIP_LINES] lines, then `+N`
 * for every reference not shown, whether past [max] or past the last line.
 */
@Composable
private fun SummaryChips(references: List<CatalogItem>, max: Int, onClick: (CatalogItem) -> Unit) {
	val shown = references.take(max)
	SubcomposeLayout(modifier = Modifier.padding(top = 4.dp)) { constraints ->
		val gap = 6.dp.roundToPx()
		val loose = constraints.copy(minWidth = 0, minHeight = 0)
		val chips = subcompose("chips") {
			shown.forEach { item ->
				HdReferenceChip(
					glyph = item.hdMark(),
					swatchColor = item.hdSwatchColor(),
					glyphColor = item.hdGlyphColor(),
					name = item.name.ellipsize(SUMMARY_CHIP_NAME_CHARS),
					onClick = { onClick(item) },
					onAction = null,
					actionContentDescription = "",
				)
			}
		}.map { it.measure(loose) }

		// The most chips that fit with the `+N` for the rest after them.
		var lines: List<List<Placeable>>? = null
		var count = chips.size
		while (lines == null) {
			val hidden = references.size - count
			val more = if (hidden > 0) {
				subcompose("more-$hidden") { SummaryMore(hidden) }.single().measure(loose)
			} else {
				null
			}
			lines = wrapLines(chips.take(count) + listOfNotNull(more), constraints.maxWidth, gap)
				.takeIf { it.size <= SUMMARY_CHIP_LINES || count == 0 }
			count--
		}

		val heights = lines.map { line -> line.maxOf { it.height } }
		val height = heights.sum() + gap * (lines.size - 1).coerceAtLeast(0)
		val width = lines.maxOfOrNull { line -> line.sumOf { it.width } + gap * (line.size - 1) } ?: 0
		layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height.coerceAtLeast(constraints.minHeight)) {
			var y = 0
			lines.forEachIndexed { index, line ->
				var x = 0
				line.forEach { placeable ->
					placeable.placeRelative(x, y + (heights[index] - placeable.height) / 2)
					x += placeable.width + gap
				}
				y += heights[index] + gap
			}
		}
	}
}

/** [items] wrapped into lines no wider than [maxWidth], one item a line where an item alone is wider. */
private fun wrapLines(items: List<Placeable>, maxWidth: Int, gap: Int): List<List<Placeable>> {
	val lines = mutableListOf<MutableList<Placeable>>()
	var x = 0
	items.forEach { item ->
		val line = lines.lastOrNull()
		if (line != null && x + gap + item.width <= maxWidth) {
			line += item
			x += gap + item.width
		} else {
			lines += mutableListOf(item)
			x = item.width
		}
	}
	return lines
}

@Composable
private fun SummaryMore(count: Int) {
	HdMonoLabel(text = Res.string.timeline_selection_more.get(count))
}
