import androidx.compose.ui.graphics.Color
import com.darkrockstudios.apps.hammer.common.compose.theme.DarkHammerColors
import com.darkrockstudios.apps.hammer.common.compose.theme.HammerExtendedColors
import com.darkrockstudios.apps.hammer.common.compose.theme.LaneShade
import com.darkrockstudios.apps.hammer.common.compose.theme.LightHammerColors
import com.darkrockstudios.apps.hammer.common.compose.theme.hueDegrees
import com.darkrockstudios.apps.hammer.common.compose.theme.laneShade
import com.darkrockstudios.apps.hammer.common.compose.theme.md_theme_dark_onSurface
import com.darkrockstudios.apps.hammer.common.compose.theme.md_theme_dark_surface
import com.darkrockstudios.apps.hammer.common.compose.theme.md_theme_light_onSurface
import com.darkrockstudios.apps.hammer.common.compose.theme.md_theme_light_surface
import com.darkrockstudios.apps.hammer.common.compose.theme.relativeLuminance
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanePaletteTest {

	private class Theme(val name: String, val colors: HammerExtendedColors, val surface: Color, val ink: Color)

	private val themes = listOf(
		Theme("light", LightHammerColors, md_theme_light_surface, md_theme_light_onSurface),
		Theme("dark", DarkHammerColors, md_theme_dark_surface, md_theme_dark_onSurface),
	)

	@Test
	fun `Every lane colour's shades differ in luminance in both themes`() {
		for (theme in themes) {
			theme.colors.lanePalette.forEachIndexed { index, base ->
				val luminances = LaneShade.entries.associateWith {
					relativeLuminance(laneShade(base, it, theme.surface, theme.ink))
				}
				for (a in LaneShade.entries) for (b in LaneShade.entries) {
					if (a >= b) continue
					val distance = abs(luminances.getValue(a) - luminances.getValue(b))
					assertTrue(
						distance >= MIN_SHADE_LUMINANCE_DISTANCE,
						"${theme.name} lane $index: $a and $b are $distance apart in luminance",
					)
				}
			}
		}
	}

	@Test
	fun `Consecutive lane colours are far apart in hue in both themes`() {
		for (theme in themes) {
			val palette = theme.colors.lanePalette
			palette.indices.forEach { index ->
				val next = (index + 1) % palette.size
				val distance = hueDistance(hueDegrees(palette[index]), hueDegrees(palette[next]))
				assertTrue(
					distance >= MIN_NEIGHBOUR_HUE_DISTANCE,
					"${theme.name} lanes $index and $next are $distance degrees apart in hue",
				)
			}
		}
	}

	@Test
	fun `Lane colours repeat by position`() {
		val colors = LightHammerColors
		val size = colors.lanePalette.size

		assertEquals(colors.lanePalette[0], colors.laneColor(0))
		assertEquals(colors.lanePalette[1], colors.laneColor(size + 1))
		assertEquals(colors.lanePalette[size - 1], colors.laneColor(-1))
	}

	private fun hueDistance(a: Float, b: Float): Float {
		val d = abs(a - b)
		return minOf(d, 360f - d)
	}

	private companion object {
		const val MIN_SHADE_LUMINANCE_DISTANCE = 0.08f
		const val MIN_NEIGHBOUR_HUE_DISTANCE = 40f
	}
}
