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
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.common.timeline.layout.LabelStyle
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineTextMeasurer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * Measures timeline labels with the fonts they are drawn in, so the geometry wraps and ellipsizes
 * against real widths. Widths are cached per string and style.
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
		widths.getOrPut(text to style) { measure(text, style).size.width / density.density }

	override fun lineHeightDp(style: LabelStyle): Float =
		lineHeights.getOrPut(style) { measure("Ag", style).size.height / density.density }
}

/**
 * Phone labels stay small so the ledger fits beside the rails; desktop labels are larger. Every call
 * returns its own measurer, caches included, so one used off the main thread shares nothing mutable
 * with one used to draw, apart from what [fontFamilyResolver] shares.
 */
@Composable
fun rememberTimelineTextMeasurer(
	phone: Boolean = false,
	fontFamilyResolver: FontFamily.Resolver = LocalFontFamilyResolver.current,
): ComposeTimelineTextMeasurer {
	val density = LocalDensity.current
	val layoutDirection = LocalLayoutDirection.current
	val textMeasurer = remember(fontFamilyResolver, density, layoutDirection) {
		TextMeasurer(fontFamilyResolver, density, layoutDirection, cacheSize = 512)
	}
	val mono = MaterialTheme.typography.labelSmall.fontFamily
	val body = MaterialTheme.typography.bodySmall.fontFamily
	return remember(textMeasurer, density, mono, body, phone) {
		val monoStyle = TextStyle(fontFamily = mono, letterSpacing = 0.sp)
		val bodyStyle = TextStyle(fontFamily = body, letterSpacing = 0.sp)
		val styles = if (phone) {
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
		ComposeTimelineTextMeasurer(textMeasurer, density) { styles.getValue(it) }
	}
}

/**
 * A measurer for laying a timeline out on [LocalTimelineLayoutContext], away from the main thread.
 * It resolves fonts through a resolver of its own, so text measured here shares no unsynchronized
 * font cache with the rest of the UI.
 */
@Composable
internal fun rememberTimelineLayoutMeasurer(phone: Boolean): ComposeTimelineTextMeasurer =
	rememberTimelineTextMeasurer(phone, rememberBackgroundFontFamilyResolver())

/** A font resolver for measuring text off the main thread, owned by its caller. */
@Composable
internal expect fun rememberBackgroundFontFamilyResolver(): FontFamily.Resolver

/** Runs one timeline layout at a time, so a layout measurer is never used by two threads at once. */
val TimelineLayoutDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

/**
 * Where timeline layouts are computed. Tests and previews provide
 * [kotlin.coroutines.EmptyCoroutineContext] to lay out on the composition's own thread, under its
 * clock.
 */
val LocalTimelineLayoutContext = staticCompositionLocalOf<CoroutineContext> { TimelineLayoutDispatcher }
