package com.darkrockstudios.apps.hammer.common.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
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
	val danger: Color,
	val characterPalette: List<Color>,
) {
	fun colorFor(type: EntryType): Color = when (type) {
		EntryType.PERSON -> person
		EntryType.PLACE -> place
		EntryType.THING -> thing
		EntryType.EVENT -> event
		EntryType.IDEA -> idea
	}

	fun colorForCharacter(entryId: Int): Color =
		characterPalette[(entryId.hashCode() and Int.MAX_VALUE) % characterPalette.size]

	fun colorForCharacter(name: String): Color =
		characterPalette[(name.hashCode() and Int.MAX_VALUE) % characterPalette.size]
}

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

val LightHammerColors = HammerExtendedColors(
	person = Color(0xFFB14A2C),
	place = Color(0xFF4F7A3A),
	thing = Color(0xFF3A5FA0),
	event = Color(0xFFA17430),
	idea = Color(0xFF6F4E97),
	success = Color(0xFF2E7D32),
	danger = Color(0xFFC62828),
	characterPalette = LightCharacterPalette,
)

val DarkHammerColors = HammerExtendedColors(
	person = Color(0xFFE07B5C),
	place = Color(0xFF8FBF7F),
	thing = Color(0xFF7B9FD4),
	event = Color(0xFFE0B05C),
	idea = Color(0xFFA88FCC),
	success = Color(0xFF7BC97D),
	danger = Color(0xFFE57373),
	characterPalette = DarkCharacterPalette,
)

val LocalHammerColors = staticCompositionLocalOf { DarkHammerColors }

object HammerTheme {
	val extendedColors: HammerExtendedColors
		@Composable
		@ReadOnlyComposable
		get() = LocalHammerColors.current
}
