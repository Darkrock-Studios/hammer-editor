package com.darkrockstudios.apps.hammer.common.compose.designsystem

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType

/**
 * Single-character glyph keyed to an [EntryType]. Used in postage-stamp
 * tags and the hairline filter bar so the visual language stays
 * consistent between per-card affordance and category filter.
 *
 * Person ☉ · Place ◇ · Thing ✦ · Event ⚑ · Idea ✶
 */
fun EntryType.glyph(): String = when (this) {
	EntryType.PERSON -> "☉"
	EntryType.PLACE -> "◇"
	EntryType.THING -> "✦"
	EntryType.EVENT -> "⚑"
	EntryType.IDEA -> "✶"
}

/** Glyph used for the "All" / unfiltered position in [HdEntryFilterBar]. */
const val HD_ALL_GLYPH: String = "∗"
