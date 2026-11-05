package com.darkrockstudios.apps.hammer.common.timeline.drawing

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.common.timeline.layout.LabelStyle
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineTextMeasurer
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * Measures timeline labels with the fonts they are drawn in, so the geometry wraps and ellipsizes
 * against real widths. Widths are cached per string and style, up to a bound, since a layout host's
 * measurer lives as long as the app.
 */
class ComposeTimelineTextMeasurer(
	val textMeasurer: TextMeasurer,
	private val density: Density,
	private val styles: (LabelStyle) -> TextStyle,
) : TimelineTextMeasurer {
	private val widths = HashMap<Pair<String, LabelStyle>, Float>()
	private val lineHeights = HashMap<LabelStyle, Float>()

	fun style(style: LabelStyle): TextStyle = styles(style)

	fun measure(text: String, style: LabelStyle): TextLayoutResult =
		textMeasurer.measure(text, styles(style), softWrap = false, density = density)

	override fun widthDp(text: String, style: LabelStyle): Float =
		widths.getOrPut(text to style) {
			if (widths.size >= MAX_CACHED_WIDTHS) widths.clear()
			measure(text, style).size.width / density.density
		}

	override fun lineHeightDp(style: LabelStyle): Float =
		lineHeights.getOrPut(style) { measure("Ag", style).size.height / density.density }
}

private const val MAX_CACHED_WIDTHS = 8192

/** The density, direction and font families a timeline's labels are measured in. */
internal data class TimelineLabelFonts(
	val phone: Boolean,
	val density: Float,
	val fontScale: Float,
	val layoutDirection: LayoutDirection,
	val mono: FontFamily?,
	val body: FontFamily?,
) {
	/** A measurer of its own, caches included, resolving fonts through [fontFamilyResolver]. */
	fun measurer(fontFamilyResolver: FontFamily.Resolver): ComposeTimelineTextMeasurer {
		val density = Density(density, fontScale)
		val textMeasurer = TextMeasurer(fontFamilyResolver, density, layoutDirection, cacheSize = 512)
		val styles = labelStyles()
		return ComposeTimelineTextMeasurer(textMeasurer, density) { styles.getValue(it) }
	}

	/** Phone labels stay small so the ledger fits beside the rails; desktop labels are larger. */
	private fun labelStyles(): Map<LabelStyle, TextStyle> {
		val monoStyle = TextStyle(fontFamily = mono, letterSpacing = 0.sp)
		val bodyStyle = TextStyle(fontFamily = body, letterSpacing = 0.sp)
		return if (phone) {
			mapOf(
				LabelStyle.Axis to monoStyle.copy(fontSize = 11.sp),
				LabelStyle.LaneHeader to monoStyle.copy(fontSize = 10.sp, letterSpacing = 0.5.sp),
				LabelStyle.BandLabel to bodyStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
				LabelStyle.BandLabelSmall to bodyStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
				LabelStyle.BarLabel to monoStyle.copy(fontSize = 10.sp),
				LabelStyle.Ledger to monoStyle.copy(fontSize = 10.sp),
				LabelStyle.LedgerMilestone to monoStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
				LabelStyle.LedgerSub to monoStyle.copy(fontSize = 9.sp),
			)
		} else {
			mapOf(
				LabelStyle.Axis to monoStyle.copy(fontSize = 12.sp),
				LabelStyle.LaneHeader to monoStyle.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
				LabelStyle.BandLabel to bodyStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
				LabelStyle.BandLabelSmall to bodyStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
				LabelStyle.BarLabel to monoStyle.copy(fontSize = 12.sp),
				LabelStyle.Ledger to monoStyle.copy(fontSize = 13.sp),
				LabelStyle.LedgerMilestone to monoStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
				LabelStyle.LedgerSub to monoStyle.copy(fontSize = 11.sp),
			)
		}
	}
}

@Composable
internal fun rememberTimelineLabelFonts(phone: Boolean): TimelineLabelFonts {
	val density = LocalDensity.current
	val layoutDirection = LocalLayoutDirection.current
	val mono = MaterialTheme.typography.labelSmall.fontFamily
	val body = MaterialTheme.typography.bodySmall.fontFamily
	return remember(phone, density.density, density.fontScale, layoutDirection, mono, body) {
		TimelineLabelFonts(phone, density.density, density.fontScale, layoutDirection, mono, body)
	}
}

/** A measurer for drawing timeline labels on the main thread, with the UI's own fonts. */
@Composable
fun rememberTimelineTextMeasurer(phone: Boolean = false): ComposeTimelineTextMeasurer {
	val fonts = rememberTimelineLabelFonts(phone)
	val fontFamilyResolver = LocalFontFamilyResolver.current
	return remember(fonts, fontFamilyResolver) { fonts.measurer(fontFamilyResolver) }
}

/**
 * Lays dated timelines out on [context], one at a time. Its measurers, and the font resolver they
 * share, live as long as the host, so fonts and label widths stay warm from one visit to the next.
 * They are only ever used on [context], so they share no unsynchronized font cache with the UI.
 */
class TimelineLayoutHost(val context: CoroutineContext) {
	private var fontFamilyResolver: FontFamily.Resolver? = null
	private val measurers = HashMap<TimelineLabelFonts, ComposeTimelineTextMeasurer>()

	/** Call only on [context]. [newResolver] makes the host's font resolver the first time one is needed. */
	internal fun measurer(fonts: TimelineLabelFonts, newResolver: () -> FontFamily.Resolver): ComposeTimelineTextMeasurer {
		val resolver = fontFamilyResolver ?: newResolver().also { fontFamilyResolver = it }
		return measurers[fonts] ?: run {
			if (measurers.size >= MAX_HOST_MEASURERS) measurers.clear()
			fonts.measurer(resolver).also { measurers[fonts] = it }
		}
	}
}

private const val MAX_HOST_MEASURERS = 4

/**
 * Makes a font resolver for a [TimelineLayoutHost], which outlives any one screen. Only the
 * returned function is remembered; the resolver is made when the host first needs it.
 */
@Composable
internal expect fun rememberBackgroundFontFamilyResolverFactory(): () -> FontFamily.Resolver

/** The app's one layout host: one layout at a time, off the main thread. */
val DefaultTimelineLayoutHost = TimelineLayoutHost(Dispatchers.Default.limitedParallelism(1))

/**
 * Where timeline layouts are computed. Tests and previews provide a host on
 * [kotlin.coroutines.EmptyCoroutineContext] to lay out on the composition's own thread, under its
 * clock.
 */
val LocalTimelineLayoutHost = staticCompositionLocalOf { DefaultTimelineLayoutHost }
