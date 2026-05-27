package data.versioncheck

import com.darkrockstudios.apps.hammer.common.data.versioncheck.GithubReleaseInfo
import com.darkrockstudios.apps.hammer.common.data.versioncheck.ShouldNotifyOfUpdateUseCase
import com.darkrockstudios.apps.hammer.common.data.versioncheck.VersionCheckRepository
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShouldNotifyOfUpdateUseCaseTest {

	private val useCase = ShouldNotifyOfUpdateUseCase()

	private fun result(
		tag: String?,
		isNewVersionAvailable: Boolean = true,
	) = VersionCheckRepository.VersionCheckResult(
		latestRelease = tag?.let {
			GithubReleaseInfo(
				tagName = it,
				name = "Release $it",
				body = "body",
				htmlUrl = "https://example/$it",
			)
		},
		isNewVersionAvailable = isNewVersionAvailable,
		currentVersion = "v1.0.0",
	)

	@Test
	fun `notifies when new version and no dismissal`() {
		assertTrue(useCase(result("v1.2.4"), dismissed = null))
	}

	@Test
	fun `does not notify when no new version available`() {
		assertFalse(useCase(result("v1.2.4", isNewVersionAvailable = false), dismissed = null))
	}

	@Test
	fun `does not notify when latest release is null`() {
		assertFalse(useCase(result(tag = null), dismissed = null))
	}

	@Test
	fun `does not notify when latest version equals dismissed`() {
		assertFalse(useCase(result("v1.2.4"), dismissed = "v1.2.4"))
	}

	@Test
	fun `partial release notifies users who haven't dismissed`() {
		assertTrue(useCase(result("v1.2.4+google-play"), dismissed = null))
	}

	@Test
	fun `dismissing a partial release suppresses the eventual full release at the same version`() {
		// User dismissed v1.2.4+google-play; later v1.2.4 (full) arrives —
		// they shouldn't be re-prompted for the same nominal version.
		assertFalse(useCase(result("v1.2.4"), dismissed = "v1.2.4+google-play"))
	}

	@Test
	fun `dismissing a full release suppresses a later partial release at the same version`() {
		assertFalse(useCase(result("v1.2.4+fdroid"), dismissed = "v1.2.4"))
	}

	@Test
	fun `partial release of newer version still notifies even if older was dismissed`() {
		assertTrue(useCase(result("v1.2.5+google-play"), dismissed = "v1.2.4"))
	}
}
