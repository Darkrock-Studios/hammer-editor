package repositories.writingactivity

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityDatasource
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityRepository
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import utils.TestClock
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/** Saves and re-anchors that reach the tracker in a different order than their text was captured. */
class WritingActivityOrderingTest : BaseTest() {

	private lateinit var repository: WritingActivityRepository
	private lateinit var tracker: WritingSessionTracker
	private val clock = TestClock(Clock.System)

	private val projectDef = ProjectDef(
		name = "Test Project",
		path = "/projects/Test Project".toPath().toHPath(),
	)

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()
		val fileSystem = FakeFileSystem()
		fileSystem.createDirectories("/projects/Test Project".toPath())
		val settings: GlobalSettingsStore = mockk(relaxed = true)
		coEvery { settings.ensureInstallId() } returns "device-test"
		every { settings.deviceLabelOrDefault() } returns "Test Device"
		repository = WritingActivityRepository(
			WritingActivityDatasource(fileSystem, createTomlSerializer(), projectDef),
			settings,
			projectDef,
		)
		tracker = WritingSessionTracker(repository, clock, projectDef)
	}

	private suspend fun save(content: String, revision: Long) =
		tracker.onSceneSaved(1, content, UpdateSource.Editor, revision)

	private suspend fun recorded() = repository.loadOwnLog().sessions.sumOf { it.wordsWritten }

	@Test
	fun `a save older than one already counted changes nothing`() = runTest {
		tracker.rememberBaseline(1, "I went home", revision = 1)
		save("I went home and ate dinner", revision = 3)

		val late = save("I went home and ate", revision = 2)
		tracker.replaceBaseline(1, "Synced text from somewhere else", revision = 4)
		val afterSync = save("Synced text from somewhere else and more", revision = 5)

		assertEquals(0, late)
		assertEquals(2, afterSync)
		assertEquals(5, recorded())
	}

	@Test
	fun `a save captured before a re-anchor is credited against the text it was typed into`() = runTest {
		tracker.rememberBaseline(1, "I went home", revision = 1)
		tracker.replaceBaseline(1, "Synced text from somewhere else", revision = 3)

		val late = save("I went home and ate", revision = 2)
		val afterSync = save("Synced text from somewhere else indeed", revision = 4)

		assertEquals(2, late)
		assertEquals(1, afterSync)
	}

	@Test
	fun `a save captured before two re-anchors is credited against the text it was typed into`() = runTest {
		tracker.rememberBaseline(1, "I went home", revision = 1)
		tracker.replaceBaseline(1, "Synced text from somewhere else", revision = 3)
		tracker.replaceBaseline(1, "Synced again from another device", revision = 4)

		assertEquals(2, save("I went home and ate", revision = 2))
	}

	@Test
	fun `a late save keeps the words already typed into the anchor it was typed into`() = runTest {
		tracker.rememberBaseline(1, "I went home", revision = 1)
		save("I went home and", revision = 2)
		tracker.replaceBaseline(1, "Synced text from somewhere else", revision = 4)

		assertEquals(1, save("I went home and ate", revision = 3))
		assertEquals(2, recorded())
	}

	@Test
	fun `a save older than the replaced anchor's last save changes nothing`() = runTest {
		tracker.rememberBaseline(1, "I went home", revision = 1)
		save("I went home and dined", revision = 3)
		tracker.replaceBaseline(1, "Synced text from somewhere else", revision = 4)

		assertEquals(0, save("I went home and ate", revision = 2))
		assertEquals(2, recorded())
	}

	@Test
	fun `an outside text older than the newest save does not re-anchor`() = runTest {
		tracker.rememberBaseline(1, "I went home", revision = 1)
		save("I went home and ate", revision = 3)

		tracker.onSceneSaved(1, "I went home", UpdateSource.Repository, revision = 2)

		assertEquals(0, save("I went home today", revision = 4))
	}

	@Test
	fun `a re-anchor older than the newest save changes nothing`() = runTest {
		tracker.rememberBaseline(1, "I went home", revision = 1)
		save("I went home and ate", revision = 3)

		tracker.replaceBaseline(1, "I went home", revision = 2)

		assertEquals(0, save("I went home today", revision = 4))
	}

	@Test
	fun `a save from before an epoch restart changes nothing after it`() = runTest {
		tracker.rememberBaseline(1, "Start.", revision = 1)
		save("Start. One two three.", revision = 3)
		clock.advanceTime(24.hours)
		save("Start. One two three. Four five.", revision = 4)

		val late = save("Start. One two.", revision = 2)
		tracker.replaceBaseline(1, "Synced.", revision = 5)

		assertEquals(0, late)
		assertEquals(1, save("Synced. More.", revision = 6))
	}

	@Test
	fun `a stale save into a re-anchored scene changes nothing after an epoch restart`() = runTest {
		tracker.rememberBaseline(1, "I went home", revision = 1)
		tracker.replaceBaseline(1, "Synced text from somewhere else", revision = 3)
		save("Synced text from somewhere else and more", revision = 5)
		clock.advanceTime(24.hours)
		save("Synced text from somewhere else and more today", revision = 6)

		assertEquals(0, save("Synced text from somewhere else and a wrong turn", revision = 4))
	}

	@Test
	fun `a save captured before a re-anchor still counts after an epoch restart`() = runTest {
		tracker.rememberBaseline(1, "I went home", revision = 1)
		tracker.replaceBaseline(1, "Synced text from somewhere else", revision = 3)
		clock.advanceTime(24.hours)

		assertEquals(2, save("I went home and ate", revision = 2))
	}
}
