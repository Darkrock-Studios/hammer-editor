package com.darkrockstudios.apps.hammer.common.timeline.drawing

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.common.timeline.layout.LabelStyle
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineTextMeasurer

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

/** Phone labels stay small so the ledger fits beside the rails; desktop labels are larger. */
@Composable
fun rememberTimelineTextMeasurer(phone: Boolean = false): ComposeTimelineTextMeasurer {
	val textMeasurer = rememberTextMeasurer(cacheSize = 512)
	val density = LocalDensity.current
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
