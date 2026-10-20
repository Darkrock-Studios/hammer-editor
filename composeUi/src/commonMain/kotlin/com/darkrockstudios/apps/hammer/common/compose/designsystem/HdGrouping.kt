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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * How sections are grouped. A prototype switch for the 4.0 grouping study, rendered
 * side by side by the screen gallery; the app itself only uses [Rules].
 */
enum class HdGrouping {
	/** Marginalia rule down the section's left edge, `§` numeral and rule in the header. */
	Rules,

	/** Each section sits on a flat tonal region; controls inside it are wells, not outlines. */
	Tonal,

	/** [Tonal] on warm paper stock, with the masthead's heavy-over-hairline rule across the top. */
	Ledger,

	/** [Ledger] with the heavy-beside-hairline rule down the left edge instead of across the top. */
	LedgerSide,

	/** [Tonal] on warm paper stock, with printer's crop marks outside each corner. */
	Crop,

	/**
	 * Cool-tinted regions; on wide screens each section's title hangs in a margin column
	 * beside its region instead of sitting inside it.
	 */
	Hanging,

	/** [Hanging] with [LedgerSide]'s ink spine on each region. */
	HangingSpine,

	/** [HangingSpine] with the top-right corner clipped, like a catalogue card; cards too. */
	HangingNotch;

	val isTonal: Boolean get() = this != Rules
	val hangs: Boolean get() = this == Hanging || this == HangingSpine || this == HangingNotch
}

val LocalHdGrouping = staticCompositionLocalOf { HdGrouping.Rules }

/** True inside a tonal region (any [HdGrouping] but [HdGrouping.Rules]). */
val LocalHdInRegion = staticCompositionLocalOf { false }

/**
 * Fill for a tonal region. Derived from the background rather than the scheme's
 * surface containers, which the theme leaves at Material's tinted defaults. Plain
 * [HdGrouping.Tonal] steps toward the text color; the paper styles step toward a warm
 * stock (carbon in dark); the hanging styles step toward the theme's cool surface variant.
 */
@Composable
fun hdRegionColor(): Color {
	val background = MaterialTheme.colorScheme.background
	return when (LocalHdGrouping.current) {
		HdGrouping.Ledger, HdGrouping.LedgerSide, HdGrouping.Crop ->
			if (background.luminance() > 0.5f) lerp(background, PaperStock, 0.45f)
			else lerp(background, CarbonStock, 0.7f)

		HdGrouping.Hanging, HdGrouping.HangingSpine, HdGrouping.HangingNotch -> lerp(
			background,
			MaterialTheme.colorScheme.surfaceVariant,
			if (background.luminance() > 0.5f) 0.55f else 0.35f,
		)

		else -> lerp(background, MaterialTheme.colorScheme.onSurface, 0.045f)
	}
}

private val PaperStock = Color(0xFFEFE4CC)
private val CarbonStock = Color(0xFF2F2D29)

/** Gap between a screen's top-level sections; tinted regions separate themselves, so it tightens. */
@Composable
fun hdSectionGap(default: Dp): Dp =
	when {
		LocalHdGrouping.current.hangs -> 32.dp
		LocalHdGrouping.current.isTonal -> 24.dp
		else -> default
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
	marker: String = "—",
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
			if (title != null) HdSectionHeader(marker = marker, title = title, trailing = headerTrailing)
			content()
		}
	}
}

/** The tonal region treatment: fill, any accent marks, and inner padding. */
@Composable
fun Modifier.hdRegion(): Modifier {
	val fill = hdRegionColor()
	val ink = MaterialTheme.colorScheme.onSurface
	val hairline = MaterialTheme.colorScheme.outline
	val density = LocalDensity.current
	return when (LocalHdGrouping.current) {
		HdGrouping.Ledger -> hdSurface(fill)
			.drawBehind {
				val heavy = 2.dp.toPx()
				val gap = 2.dp.toPx()
				val thin = with(density) { Dp.Hairline.toPx().coerceAtLeast(1f) }
				drawRect(ink, size = Size(size.width, heavy))
				drawRect(hairline, topLeft = Offset(0f, heavy + gap), size = Size(size.width, thin))
			}
			.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 20.dp)

		HdGrouping.LedgerSide -> hdSurface(fill)
			.drawBehind { drawSpine(ink, hairline, heavy = 3.dp, density = density) }
			.padding(start = 26.dp, end = 20.dp, top = 20.dp, bottom = 20.dp)

		HdGrouping.HangingSpine -> hdSurface(fill)
			.drawBehind { drawSpine(ink, hairline, heavy = 3.dp, density = density) }
			.padding(start = 26.dp, end = 20.dp, top = 20.dp, bottom = 20.dp)

		HdGrouping.HangingNotch -> hdSurface(fill, NotchShape)
			.drawBehind { drawSpine(ink, hairline, heavy = 3.dp, density = density) }
			.padding(start = 26.dp, end = 20.dp, top = 20.dp, bottom = 20.dp)

		HdGrouping.Crop -> hdSurface(fill)
			.drawBehind { drawCropMarks(ink.copy(alpha = 0.7f)) }
			.padding(20.dp)

		else -> hdSurface(fill).padding(20.dp)
	}
}

/**
 * The surface of a browse-grid card. [Rules] keeps the outlined grey card; the tonal styles
 * put it on the region fill, and [HdGrouping.LedgerSide] adds a lighter spine than a section's
 * and insets the content to clear it.
 */
@Composable
fun Modifier.hdCard(): Modifier {
	val grouping = LocalHdGrouping.current
	if (!grouping.isTonal) {
		return background(MaterialTheme.colorScheme.surfaceContainerLow, RectangleShape)
			.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, RectangleShape)
	}
	val fill = hdSurface(hdRegionColor(), if (grouping == HdGrouping.HangingNotch) NotchShape else RectangleShape)
	if (grouping != HdGrouping.LedgerSide) return fill
	val ink = MaterialTheme.colorScheme.onSurface
	val hairline = MaterialTheme.colorScheme.outline
	val density = LocalDensity.current
	return fill
		.drawBehind { drawSpine(ink, hairline, heavy = 2.dp, density = density) }
		.padding(start = 5.dp)
}

/** Square except for a 45° cut across the top-right corner. */
private object NotchShape : Shape {
	override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
		val cut = minOf(with(density) { 14.dp.toPx() }, size.width / 4f, size.height / 4f)
		return Outline.Generic(
			Path().apply {
				moveTo(0f, 0f)
				lineTo(size.width - cut, 0f)
				lineTo(size.width, cut)
				lineTo(size.width, size.height)
				lineTo(0f, size.height)
				close()
			}
		)
	}
}

/** A heavy ink rule down the left edge with a hairline beside it. */
private fun DrawScope.drawSpine(ink: Color, hairline: Color, heavy: Dp, density: Density) {
	val heavyPx = heavy.toPx()
	val gap = 2.dp.toPx()
	val thin = with(density) { Dp.Hairline.toPx().coerceAtLeast(1f) }
	drawRect(ink, size = Size(heavyPx, size.height))
	drawRect(hairline, topLeft = Offset(heavyPx + gap, 0f), size = Size(thin, size.height))
}

/** Printer's crop marks: short strokes outside each corner, set off from the trim. */
private fun DrawScope.drawCropMarks(color: Color) {
	val stroke = 1.dp.toPx()
	val gap = 4.dp.toPx()
	val len = 10.dp.toPx()
	val w = size.width
	val h = size.height
	for ((x, y, dx, dy) in listOf(
		listOf(0f, 0f, -1f, -1f), listOf(w, 0f, 1f, -1f),
		listOf(0f, h, -1f, 1f), listOf(w, h, 1f, 1f),
	)) {
		drawLine(color, Offset(x + dx * gap, y), Offset(x + dx * (gap + len), y), stroke)
		drawLine(color, Offset(x, y + dy * gap), Offset(x, y + dy * (gap + len)), stroke)
	}
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
