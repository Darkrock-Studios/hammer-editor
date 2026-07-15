package components.protocolmismatch

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.darkrockstudios.apps.hammer.base.GITHUB_URL
import com.darkrockstudios.apps.hammer.common.components.protocolmismatch.ProtocolMismatchComponent
import com.darkrockstudios.apps.hammer.common.data.protocolmismatch.ProtocolMismatchInfo
import com.darkrockstudios.apps.hammer.common.data.versioncheck.GithubReleaseInfo
import com.darkrockstudios.apps.hammer.common.data.versioncheck.VersionCheckDataSource
import com.darkrockstudios.apps.hammer.common.data.versioncheck.VersionCheckRepository
import com.darkrockstudios.apps.hammer.common.util.UrlLauncher
import com.darkrockstudios.apps.hammer.common.util.getAppVersionString
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProtocolMismatchComponentTest : BaseTest() {

	private lateinit var lifecycle: LifecycleRegistry
	private lateinit var context: DefaultComponentContext
	private lateinit var dataSource: VersionCheckDataSource
	private lateinit var urlLauncher: UrlLauncher
	private var dismissed = false

	private val release = GithubReleaseInfo(
		tagName = "v9.9.9",
		name = "Big Release",
		body = "notes",
		htmlUrl = "https://github.com/Darkrock-Studios/hammer-editor/releases/tag/v9.9.9",
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		lifecycle = LifecycleRegistry()
		context = DefaultComponentContext(lifecycle = lifecycle)
		dataSource = mockk(relaxed = true)
		urlLauncher = mockk(relaxed = true)
		dismissed = false

		setupKoin(
			module {
				single { dataSource } bind VersionCheckDataSource::class
				single { VersionCheckRepository(get()) }
				single { urlLauncher } bind UrlLauncher::class
			}
		)
		lifecycle.resume()
	}

	private fun newComponent(
		clientVersion: Int = 3,
		serverVersion: Int? = 5,
	) = ProtocolMismatchComponent(
		componentContext = context,
		info = ProtocolMismatchInfo(
			clientProtocolVersion = clientVersion,
			serverProtocolVersion = serverVersion,
		),
		dismissDialog = { dismissed = true },
	)

	@Test
	fun `populates state from the latest release`() = runTest {
		coEvery { dataSource.fetchLatestRelease() } returns release

		val component = newComponent()
		advanceUntilIdle()

		val state = component.state.value
		assertEquals("v9.9.9", state.latestVersionTag)
		assertEquals(release.htmlUrl, state.releaseUrl)
		assertEquals(getAppVersionString(), state.currentVersion)
	}

	@Test
	fun `a newer server protocol means the client is behind`() = runTest {
		coEvery { dataSource.fetchLatestRelease() } returns release

		val component = newComponent(clientVersion = 3, serverVersion = 5)
		advanceUntilIdle()

		assertTrue(component.state.value.clientIsBehind)
	}

	@Test
	fun `an older server protocol means the server is behind`() = runTest {
		coEvery { dataSource.fetchLatestRelease() } returns release

		val component = newComponent(clientVersion = 5, serverVersion = 3)
		advanceUntilIdle()

		assertFalse(component.state.value.clientIsBehind)
	}

	@Test
	fun `openReleaseUrl opens the release page`() = runTest {
		coEvery { dataSource.fetchLatestRelease() } returns release

		val component = newComponent()
		advanceUntilIdle()
		component.openReleaseUrl()

		verify { urlLauncher.openInBrowser(release.htmlUrl) }
	}

	@Test
	fun `openReleaseUrl falls back to the releases page when no release is known`() = runTest {
		coEvery { dataSource.fetchLatestRelease() } returns null

		val component = newComponent()
		advanceUntilIdle()
		assertNull(component.state.value.releaseUrl)

		component.openReleaseUrl()

		verify { urlLauncher.openInBrowser("${GITHUB_URL}releases") }
	}

	@Test
	fun `dismiss invokes the dismiss callback`() = runTest {
		coEvery { dataSource.fetchLatestRelease() } returns release

		val component = newComponent()
		advanceUntilIdle()
		component.dismiss()

		assertTrue(dismissed)
	}
}
