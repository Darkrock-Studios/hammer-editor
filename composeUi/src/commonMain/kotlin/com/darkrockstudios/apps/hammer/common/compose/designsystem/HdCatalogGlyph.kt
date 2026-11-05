package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind

/**
 * Glyphs for the things a project holds besides entries, which use `EntryType.glyph()`:
 *
 * Scene § · Note ¶ · Timeline event ◷
 */
const val HD_SCENE_GLYPH: String = "§"
const val HD_NOTE_GLYPH: String = "¶"
const val HD_EVENT_GLYPH: String = "◷"

/** An entry's type glyph, or its kind's glyph for anything else. */
fun CatalogItem.hdMark(): String = entryType?.glyph() ?: when (kind) {
	CatalogKind.Scene -> HD_SCENE_GLYPH
	CatalogKind.Note -> HD_NOTE_GLYPH
	CatalogKind.Entry -> HD_ALL_GLYPH
	CatalogKind.Event -> HD_EVENT_GLYPH
}

/** A timeline entry's glyph: a plain event, a placed scene, or a placed EVENT entry. */
fun TimelineKind.hdMark(): String = when (this) {
	TimelineKind.EVENT -> HD_EVENT_GLYPH
	TimelineKind.SCENE -> HD_SCENE_GLYPH
	TimelineKind.ENTRY -> EntryType.EVENT.glyph()
}

/** The swatch behind [hdMark]: an entry's type colour, a neutral ink for the other kinds. */
@Composable
fun CatalogItem.hdSwatchColor(): Color =
	entryType?.let { LocalHammerColors.current.colorFor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant

/** The colour [hdMark] is drawn in on [hdSwatchColor]. */
@Composable
fun CatalogItem.hdGlyphColor(): Color =
	if (entryType != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface
