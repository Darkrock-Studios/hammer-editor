package components.projectselection.aboutapp

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutAppComponent
import com.darkrockstudios.apps.hammer.common.startupBanner
import com.darkrockstudios.apps.hammer.common.util.UrlLauncher
import io.ktor.http.Url
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals

class AboutAppComponentTest : BaseTest() {

	private class RecordingUrlLauncher : UrlLauncher {
		val opened = mutableListOf<String>()

		override fun openInBrowser(url: String) {
			opened += url
		}
	}

	private lateinit var urlLauncher: RecordingUrlLauncher
	private lateinit var component: AboutAppComponent

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()

		urlLauncher = RecordingUrlLauncher()
		val lifecycle = LifecycleRegistry()
		component = AboutAppComponent(
			componentContext = DefaultComponentContext(lifecycle = lifecycle),
			urlLauncher = urlLauncher,
			updateShouldClose = {},
			onShowChangelog = {},
		)
		lifecycle.resume()
	}

	@Test
	fun `reporting a bug opens the bug report form with this install's details`() {
		component.reportBug()

		val url = Url(urlLauncher.opened.single())
		assertEquals("/Darkrock-Studios/hammer-editor/issues/new", url.encodedPath)
		assertEquals("bug_report.yml", url.parameters["template"])
		assertEquals(startupBanner(), url.parameters["environment"])
	}
}
