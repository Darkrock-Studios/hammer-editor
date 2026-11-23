package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How sections are grouped. A prototype switch for the 4.0 grouping study, rendered
 * side by side by the screen gallery; the app itself only uses [Rules].
 */
enum class HdGrouping {
	/** Marginalia rule down the section's left edge, `§` numeral and rule in the header. */
	Rules,

	/**
	 * Cool-tinted regions with an ink spine down the left edge; on wide screens each section's
	 * title hangs in a margin column beside its region.
	 */
	HangingSpine;

	val isTonal: Boolean get() = this != Rules
	val hangs: Boolean get() = this == HangingSpine
}

val LocalHdGrouping = staticCompositionLocalOf { HdGrouping.Rules }

/** True inside a tonal region. */
val LocalHdInRegion = staticCompositionLocalOf { false }

/** Fill for a tonal region: the background stepped toward the theme's cool surface variant. */
@Composable
fun hdRegionColor(): Color {
	val background = MaterialTheme.colorScheme.background
	return lerp(
		background,
		MaterialTheme.colorScheme.surfaceVariant,
		if (background.luminance() > 0.5f) 0.55f else 0.35f,
	)
}

private val HangWidth = 240.dp
private val HangGap = 28.dp

/** True when section titles hang beside their regions: a hanging style on a wide screen. */
@Composable
fun hdHangsHeads(): Boolean = LocalHdGrouping.current.hangs && LocalScreenCharacteristic.current.isWide

/** A content column's max width, grown by the hang so hanging titles don't squeeze the regions. */
@Composable
fun hdColumnMaxWidth(base: Dp): Dp = if (hdHangsHeads()) base + HangWidth + HangGap else base

/**
 * A titled section body: hanging [head] in a margin column, then [body]. Titles align
 * to the region's edge, like a margin note pointing at it.
 */
@Composable
internal fun HdHangingRow(
	modifier: Modifier = Modifier,
	head: @Composable ColumnScope.() -> Unit,
	body: @Composable () -> Unit,
) {
	Row(modifier = modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier
				.width(HangWidth)
				.padding(top = 18.dp),
			horizontalAlignment = Alignment.End,
			verticalArrangement = Arrangement.spacedBy(6.dp),
			content = head,
		)
		Spacer(Modifier.width(HangGap))
		Box(modifier = Modifier.weight(1f)) { body() }
	}
}

/** A hanging section title, with its greebles beneath it. */
@Composable
internal fun ColumnScope.HdHangingTitle(title: String, trailing: @Composable RowScope.() -> Unit) {
	Text(
		text = title,
		style = MaterialTheme.typography.headlineSmall,
		color = MaterialTheme.colorScheme.onSurface,
		textAlign = TextAlign.End,
	)
	Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = trailing)
}

/**
 * A group of related content that isn't a full [HdHairlineSection], with an optional
 * [title]. Under a tonal [HdGrouping] it becomes a region (with the title hung beside it
 * when [hdHangsHeads]); otherwise it is a plain column, so existing layouts are unchanged.
 */
@Composable
fun HdRegion(
	modifier: Modifier = Modifier,
	contentSpacing: Dp = 16.dp,
	title: String? = null,
	headerTrailing: @Composable RowScope.() -> Unit = {},
	content: @Composable ColumnScope.() -> Unit,
) {
	val tonal = LocalHdGrouping.current.isTonal
	val inRegion = tonal || LocalHdInRegion.current
	if (hdHangsHeads()) {
		HdHangingRow(
			modifier = modifier,
			head = { if (title != null) HdHangingTitle(title, headerTrailing) },
		) {
			Column(
				modifier = Modifier.fillMaxWidth().hdRegion(),
				verticalArrangement = Arrangement.spacedBy(contentSpacing),
			) {
				CompositionLocalProvider(LocalHdInRegion provides inRegion) { content() }
			}
		}
		return
	}
	Column(
		modifier = modifier
			.fillMaxWidth()
			.then(if (tonal) Modifier.hdRegion() else Modifier),
		verticalArrangement = Arrangement.spacedBy(contentSpacing),
	) {
		CompositionLocalProvider(LocalHdInRegion provides inRegion) {
			if (title != null) HdSectionHeader(marker = "—", title = title, trailing = headerTrailing)
			content()
		}
	}
}

/** The tonal region treatment: fill, ink spine, and inner padding. */
@Composable
fun Modifier.hdRegion(): Modifier {
	val ink = MaterialTheme.colorScheme.onSurface
	val hairline = MaterialTheme.colorScheme.outline
	val density = LocalDensity.current
	return background(hdRegionColor(), RectangleShape)
		.drawBehind { drawSpine(ink, hairline, heavy = 3.dp, density = density) }
		.padding(start = 26.dp, end = 20.dp, top = 20.dp, bottom = 20.dp)
}

/** The surface of a browse-grid card: the outlined grey card, or the region fill when tonal. */
@Composable
fun Modifier.hdCard(): Modifier =
	if (LocalHdGrouping.current.isTonal) {
		background(hdRegionColor(), RectangleShape)
	} else {
		background(MaterialTheme.colorScheme.surfaceContainerLow, RectangleShape)
			.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, RectangleShape)
	}

/** A heavy ink rule down the left edge with a hairline beside it. */
private fun DrawScope.drawSpine(ink: Color, hairline: Color, heavy: Dp, density: Density) {
	val heavyPx = heavy.toPx()
	val gap = 2.dp.toPx()
	val thin = with(density) { Dp.Hairline.toPx().coerceAtLeast(1f) }
	drawRect(ink, size = Size(heavyPx, size.height))
	drawRect(hairline, topLeft = Offset(heavyPx + gap, 0f), size = Size(thin, size.height))
}

/**
 * A control's outline. Inside a tonal region the outline gives way to a flat well in
 * the page background; [keepBorder] keeps it for state cues such as a selected segment.
 */
@Composable
fun Modifier.hdControlFrame(borderColor: Color, keepBorder: Boolean = false): Modifier =
	if (LocalHdInRegion.current) {
		val well = background(MaterialTheme.colorScheme.background, RectangleShape)
		if (keepBorder) well.border(Dp.Hairline, borderColor, RectangleShape) else well
	} else {
		border(Dp.Hairline, borderColor, RectangleShape)
	}
