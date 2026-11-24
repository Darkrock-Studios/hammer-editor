import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.intro.FileRows
import com.darkrockstudios.apps.hammer.common.intro.LandscapeBox
import com.darkrockstudios.apps.hammer.common.intro.SketchWidth
import com.darkrockstudios.apps.hammer.common.intro.sketchLabelStyle
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class IntroSketchLabelTest {
	@get:Rule
	val compose = createComposeRule()

	@Test
	fun `File labels stay inside the sketch at a large font scale`() {
		val density = Density(density = 2f, fontScale = 2f)
		val unit = density.density
		val rightEdges = mutableListOf<Float>()

		compose.setContent {
			CompositionLocalProvider(LocalDensity provides density) {
				AppTheme(globalSettingsPreview) {
					val measurer = rememberTextMeasurer()
					val style = sketchLabelStyle(MaterialTheme.typography.labelSmall, LandscapeBox, unit, density)
					FileRows.forEach { row ->
						val width = measurer.measure(row.name, style).size.width / unit
						rightEdges += row.labelX + width
					}
				}
			}
		}
		compose.waitForIdle()

		assertTrue(rightEdges.isNotEmpty())
		assertTrue(rightEdges.all { it <= SketchWidth }, "label right edges: $rightEdges")
	}
}
