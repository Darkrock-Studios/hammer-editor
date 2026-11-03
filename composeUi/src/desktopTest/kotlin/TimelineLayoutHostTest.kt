import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.LayoutDirection
import com.darkrockstudios.apps.hammer.common.timeline.drawing.TimelineLabelFonts
import com.darkrockstudios.apps.hammer.common.timeline.drawing.TimelineLayoutHost
import com.darkrockstudios.apps.hammer.common.timeline.layout.LabelStyle
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TimelineLayoutHostTest {
	private fun fonts(phone: Boolean) = TimelineLabelFonts(
		phone = phone,
		density = 1f,
		fontScale = 1f,
		layoutDirection = LayoutDirection.Ltr,
		mono = FontFamily.Monospace,
		body = FontFamily.Default,
	)

	@Test
	fun `A host keeps one measurer per label style and one font resolver for every layout`() {
		val host = TimelineLayoutHost(EmptyCoroutineContext)
		var resolvers = 0
		val newResolver = {
			resolvers++
			createFontFamilyResolver()
		}

		val desktop = host.measurer(fonts(phone = false), newResolver)
		val desktopAgain = host.measurer(fonts(phone = false), newResolver)
		val phone = host.measurer(fonts(phone = true), newResolver)

		assertSame(desktop, desktopAgain)
		assertNotSame(desktop, phone)
		assertEquals(1, resolvers)
		assertTrue(desktop.widthDp("The Siege", LabelStyle.Ledger) > phone.widthDp("The Siege", LabelStyle.Ledger))
	}
}
