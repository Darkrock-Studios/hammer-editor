import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.ScreenCharacteristics
import com.darkrockstudios.apps.hammer.common.compose.SetScreenCharacteristics
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

private val WideThreshold = 720.dp

/**
 * The size classes must come from the space the UI actually occupies. The desktop
 * `calculateWindowSizeClass()` reads an AWT window, which no longer exists under the
 * Tao backend, so every window reported Compact.
 */
class ScreenCharacteristicsTest {

	@get:Rule
	val compose = createComposeRule()

	private fun characteristicsAt(width: Dp, height: Dp): ScreenCharacteristics {
		lateinit var observed: ScreenCharacteristics
		compose.setContent {
			Box(modifier = Modifier.requiredSize(width, height)) {
				SetScreenCharacteristics(WideThreshold) {
					observed = LocalScreenCharacteristic.current
				}
			}
		}
		compose.waitForIdle()
		return observed
	}

	@Test
	fun `desktop sized window is expanded`() {
		val screen = characteristicsAt(1280.dp, 900.dp)

		assertEquals(WindowWidthSizeClass.Expanded, screen.windowWidthClass)
		assertEquals(WindowHeightSizeClass.Expanded, screen.windowHeightClass)
		assertEquals(true, screen.isWide)
	}

	@Test
	fun `tablet sized window is medium`() {
		val screen = characteristicsAt(700.dp, 600.dp)

		assertEquals(WindowWidthSizeClass.Medium, screen.windowWidthClass)
		assertEquals(WindowHeightSizeClass.Medium, screen.windowHeightClass)
		assertEquals(false, screen.isWide)
	}

	@Test
	fun `phone sized window is compact`() {
		val screen = characteristicsAt(360.dp, 640.dp)

		assertEquals(WindowWidthSizeClass.Compact, screen.windowWidthClass)
		assertEquals(WindowHeightSizeClass.Medium, screen.windowHeightClass)
		assertEquals(false, screen.isWide)
	}
}
