package data.versioncheck

import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.common.data.versioncheck.GithubReleaseInfo
import com.darkrockstudios.apps.hammer.common.data.versioncheck.VersionCheckDataSource
import com.darkrockstudios.apps.hammer.common.data.versioncheck.VersionCheckRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VersionCheckRepositoryTest {

	private fun release(tag: String) = GithubReleaseInfo(
		tagName = tag,
		name = "Release $tag",
		body = "Changelog body",
		htmlUrl = "https://github.com/Darkrock-Studios/hammer-editor/releases/tag/$tag",
	)

	@Test
	fun `checkForUpdate flags new version when tag differs from current`() = runTest {
		val dataSource = mockk<VersionCheckDataSource>()
		coEvery { dataSource.fetchLatestRelease() } returns release("v99.0.0")
		val repo = VersionCheckRepository(dataSource)

		val result = repo.checkForUpdate()

		assertEquals("v99.0.0", result.latestRelease?.tagName)
		assertTrue(result.isNewVersionAvailable)
	}

	@Test
	fun `checkForUpdate reports no update when datasource returns null`() = runTest {
		val dataSource = mockk<VersionCheckDataSource>()
		coEvery { dataSource.fetchLatestRelease() } returns null
		val repo = VersionCheckRepository(dataSource)

		val result = repo.checkForUpdate()

		assertNull(result.latestRelease)
		assertFalse(result.isNewVersionAvailable)
	}

	@Test
	fun `checkForUpdate reports no update when tag equals current version`() = runTest {
		val dataSource = mockk<VersionCheckDataSource>()
		coEvery { dataSource.fetchLatestRelease() } returns release("v${BuildMetadata.APP_VERSION}")
		val repo = VersionCheckRepository(dataSource)

		val result = repo.checkForUpdate()

		assertFalse(result.isNewVersionAvailable)
	}

	@Test
	fun `checkForUpdate caches result across calls`() = runTest {
		val dataSource = mockk<VersionCheckDataSource>()
		coEvery { dataSource.fetchLatestRelease() } returns release("v99.0.0")
		val repo = VersionCheckRepository(dataSource)

		val first = repo.checkForUpdate()
		val second = repo.checkForUpdate()

		assertSame(first, second)
		coVerify(exactly = 1) { dataSource.fetchLatestRelease() }
	}

	@Test
	fun `checkForUpdate force re-fetches`() = runTest {
		val dataSource = mockk<VersionCheckDataSource>()
		coEvery { dataSource.fetchLatestRelease() } returns release("v99.0.0")
		val repo = VersionCheckRepository(dataSource)

		repo.checkForUpdate()
		repo.checkForUpdate(force = true)

		coVerify(exactly = 2) { dataSource.fetchLatestRelease() }
	}

}
