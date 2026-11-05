package com.darkrockstudios.apps.hammer.common.timeline.layout

enum class LabelStyle { Axis, LaneHeader, BandLabel, BandLabelSmall, BarLabel, Ledger, LedgerMilestone, LedgerSub }

interface TimelineTextMeasurer {
	fun widthDp(text: String, style: LabelStyle): Float
	fun lineHeightDp(style: LabelStyle): Float
}

/** Monospace approximation (advance 0.6 em, line 1.2 em) for tests and previews. */
class MonospaceEstimateMeasurer(
	private val fontSizesDp: Map<LabelStyle, Float> = DEFAULT_FONT_SIZES,
) : TimelineTextMeasurer {
	private fun size(style: LabelStyle): Float = fontSizesDp[style] ?: DEFAULT_FONT_SIZES.getValue(style)

	override fun widthDp(text: String, style: LabelStyle): Float = text.length * ADVANCE * size(style)

	override fun lineHeightDp(style: LabelStyle): Float = LINE * size(style)

	companion object {
		private const val ADVANCE = 0.6f
		private const val LINE = 1.2f

		val DEFAULT_FONT_SIZES: Map<LabelStyle, Float> = mapOf(
			LabelStyle.Axis to 12f,
			LabelStyle.LaneHeader to 11f,
			LabelStyle.BandLabel to 13f,
			LabelStyle.BandLabelSmall to 11f,
			LabelStyle.BarLabel to 12f,
			LabelStyle.Ledger to 13f,
			LabelStyle.LedgerMilestone to 13f,
			LabelStyle.LedgerSub to 11f,
		)

		/** The smaller labels a phone draws beside its rails. */
		val PHONE_FONT_SIZES: Map<LabelStyle, Float> = mapOf(
			LabelStyle.Axis to 11f,
			LabelStyle.LaneHeader to 10f,
			LabelStyle.BandLabel to 11f,
			LabelStyle.BandLabelSmall to 9f,
			LabelStyle.BarLabel to 10f,
			LabelStyle.Ledger to 10f,
			LabelStyle.LedgerMilestone to 10f,
			LabelStyle.LedgerSub to 9f,
		)
	}
}
