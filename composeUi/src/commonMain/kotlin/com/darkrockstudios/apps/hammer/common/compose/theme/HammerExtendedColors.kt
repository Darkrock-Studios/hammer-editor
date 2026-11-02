package com.darkrockstudios.apps.hammer.common.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType

@Immutable
data class HammerExtendedColors(
	val person: Color,
	val place: Color,
	val thing: Color,
	val event: Color,
	val idea: Color,
	val success: Color,
	val warning: Color,
	val danger: Color,
	val moved: Color,
	val characterPalette: List<Color>,
	val tagPalette: List<Color>,
	/** Ordered so neighbours are far apart in hue; see [laneColor]. */
	val lanePalette: List<Color>,
) {
	fun colorFor(type: EntryType): Color = when (type) {
		EntryType.PERSON -> person
		EntryType.PLACE -> place
		EntryType.THING -> thing
		EntryType.EVENT -> event
		EntryType.IDEA -> idea
	}

	fun colorForCharacter(entryId: Int): Color = characterPalette.pickByHash(entryId)

	fun colorForCharacter(name: String): Color = characterPalette.pickByHash(name)

	fun tagColor(tag: String): Color = tagPalette.pickByHash(tag)

	/** A timeline lane's colour by its position in the lane list, never by its name. */
	fun laneColor(index: Int): Color = lanePalette[index.mod(lanePalette.size)]
}

private fun List<Color>.pickByHash(key: Any): Color =
	this[(key.hashCode() and Int.MAX_VALUE) % size]

private val LightCharacterPalette = listOf(
	Color(0xFFB14A2C), // brick
	Color(0xFFA17430), // ochre
	Color(0xFF4F7A3A), // moss
	Color(0xFF2F7A77), // teal
	Color(0xFF3A5FA0), // indigo
	Color(0xFF6F4E97), // violet
	Color(0xFF9C3E6F), // mulberry
	Color(0xFF7A6E2C), // olive
	Color(0xFF4F5E73), // slate
	Color(0xFF8C4A55), // mauve
)

private val DarkCharacterPalette = listOf(
	Color(0xFFE07B5C), // coral
	Color(0xFFE0B05C), // amber
	Color(0xFF8FBF7F), // sage
	Color(0xFF6FB5B5), // teal
	Color(0xFF7B9FD4), // sky
	Color(0xFFA88FCC), // lavender
	Color(0xFFD48FA8), // rose
	Color(0xFFB5B05C), // olive
	Color(0xFF8F9FB5), // slate
	Color(0xFFB58FA0), // mauve
)

private val LightTagPalette = listOf(
	Color(0xFFC0392B), // crimson
	Color(0xFFD35400), // tangerine
	Color(0xFFB7950B), // mustard
	Color(0xFF16A085), // jade
	Color(0xFF1F8A4C), // forest
	Color(0xFF2980B9), // cobalt
	Color(0xFF1F618D), // ocean
	Color(0xFF6C3483), // indigo
	Color(0xFF884EA0), // amethyst
	Color(0xFFC71585), // magenta
	Color(0xFF7D3C98), // grape
	Color(0xFF935116), // chestnut
)

private val DarkTagPalette = listOf(
	Color(0xFFFF8787), // coral
	Color(0xFFFFA94D), // peach
	Color(0xFFFFD43B), // sunshine
	Color(0xFF51CF66), // fresh-green
	Color(0xFF5EEAD4), // aqua
	Color(0xFF4DABF7), // sky
	Color(0xFF74C0FC), // cornflower
	Color(0xFFB197FC), // lilac
	Color(0xFFDA77F2), // orchid
	Color(0xFFF783AC), // pink
	Color(0xFFFAB005), // amber
	Color(0xFFFFB4A2), // blush
)

// Lighter than the character palette so each lane keeps a visible three-step ramp (LaneShades.kt);
// LanePaletteTest holds the luminance and hue spacing.
private val LightLanePalette = listOf(
	Color(0xFFD0663F), // brick
	Color(0xFF3A8AC4), // cobalt
	Color(0xFF5E9445), // moss
	Color(0xFF9C76CC), // amethyst
	Color(0xFFC28A2E), // ochre
	Color(0xFF2E9690), // teal
	Color(0xFFD6586C), // crimson
	Color(0xFF6E78D8), // indigo
)

private val DarkLanePalette = listOf(
	Color(0xFFD8765A), // brick
	Color(0xFF58A0D8), // cobalt
	Color(0xFF74A460), // moss
	Color(0xFFA084D0), // amethyst
	Color(0xFFC8964A), // ochre
	Color(0xFF48A69E), // teal
	Color(0xFFE07082), // crimson
	Color(0xFF7C82DE), // indigo
)

val LightHammerColors = HammerExtendedColors(
	person = Color(0xFFB14A2C),
	place = Color(0xFF4F7A3A),
	thing = Color(0xFF3A5FA0),
	event = Color(0xFFA17430),
	idea = Color(0xFF6F4E97),
	success = Color(0xFF2E7D32),
	warning = Color(0xFFB58940),
	danger = Color(0xFFC62828),
	moved = Color(0xFF3A5FA0),
	characterPalette = LightCharacterPalette,
	tagPalette = LightTagPalette,
	lanePalette = LightLanePalette,
)

val DarkHammerColors = HammerExtendedColors(
	person = Color(0xFFE07B5C),
	place = Color(0xFF8FBF7F),
	thing = Color(0xFF7B9FD4),
	event = Color(0xFFE0B05C),
	idea = Color(0xFFA88FCC),
	success = Color(0xFF7BC97D),
	warning = Color(0xFFE0B36B),
	danger = Color(0xFFE57373),
	moved = Color(0xFF7B9FD4),
	characterPalette = DarkCharacterPalette,
	tagPalette = DarkTagPalette,
	lanePalette = DarkLanePalette,
)

val LocalHammerColors = staticCompositionLocalOf { DarkHammerColors }
