package repositories.writingactivity

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityDatasource
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityRepository
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import utils.TestClock
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class WritingSessionTrackerTest : BaseTest() {

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var datasource: WritingActivityDatasource
	private lateinit var repository: WritingActivityRepository
	private lateinit var globalSettingsRepository: GlobalSettingsRepository
	private lateinit var clock: TestClock
	private lateinit var tracker: WritingSessionTracker

	private val projectDef = ProjectDef(
		name = "Test Project",
		path = "/projects/Test Project".toPath().toHPath(),
	)

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()

		fileSystem = FakeFileSystem()
		fileSystem.createDirectories("/projects/Test Project".toPath())
		toml = createTomlSerializer()

		datasource = WritingActivityDatasource(fileSystem, toml, projectDef)

		globalSettingsRepository = mockk(relaxed = true)
		coEvery { globalSettingsRepository.ensureInstallId() } returns "device-test"
		every { globalSettingsRepository.deviceLabelOrDefault() } returns "Test Device"

		repository = WritingActivityRepository(datasource, globalSettingsRepository, projectDef)
		clock = TestClock(Clock.System)
		tracker = WritingSessionTracker(repository, clock, projectDef)
	}

	@Test
	fun `editor save with new words credits and persists`() = runTest {
		tracker.rememberBaseline(sceneId = 1, content = "I went home")
		val credited = tracker.onSceneSaved(
			sceneId = 1,
			newContent = "I went home and made dinner",
			source = UpdateSource.Editor,
		)
		// "and", "made", "dinner" are new — three words.
		assertEquals(3, credited)

		val log = repository.loadOwnLog()
		assertEquals(1, log.sessions.size)
		assertEquals(3, log.sessions.single().wordsWritten)
		assertEquals("Test Device", log.deviceLabel)
	}

	@Test
	fun `consecutive editor saves accumulate within one session`() = runTest {
		tracker.rememberBaseline(sceneId = 1, content = "")
		tracker.onSceneSaved(1, "first ten words to start the chapter from a blank page", UpdateSource.Editor)
		tracker.onSceneSaved(1, "first ten words to start the chapter from a blank page plus more", UpdateSource.Editor)

		val log = repository.loadOwnLog()
		assertEquals(1, log.sessions.size)
		// 11 + 2 = 13 words added across the two saves
		assertEquals(13, log.sessions.single().wordsWritten)
	}

	@Test
	fun `non-Editor sources do not credit`() = runTest {
		tracker.rememberBaseline(sceneId = 1, content = "I went home")
		val credited = tracker.onSceneSaved(
			sceneId = 1,
			newContent = "I went home and made dinner and slept well",
			source = UpdateSource.Sync,
		)
		assertEquals(0, credited)

		val log = repository.loadOwnLog()
		assertTrue(log.sessions.isEmpty(), "Sync save should not produce a session")
	}

	@Test
	fun `Drafts and Repository sources also do not credit`() = runTest {
		tracker.rememberBaseline(sceneId = 1, content = "")
		tracker.onSceneSaved(1, "draft content here", UpdateSource.Drafts)
		tracker.onSceneSaved(1, "repository content here", UpdateSource.Repository)

		val log = repository.loadOwnLog()
		assertTrue(log.sessions.isEmpty())
	}

	@Test
	fun `save without prior baseline establishes one but credits zero`() = runTest {
		// Scene was never explicitly loaded — first save shouldn't credit
		// any words because we have no reference to compare against.
		val credited = tracker.onSceneSaved(
			sceneId = 1,
			newContent = "ten words appearing for the very first time on save",
			source = UpdateSource.Editor,
		)
		assertEquals(0, credited)

		// A subsequent edit on top of the now-established baseline does credit.
		val secondCredited = tracker.onSceneSaved(
			sceneId = 1,
			newContent = "ten words appearing for the very first time on save plus three",
			source = UpdateSource.Editor,
		)
		// "plus", "three" added — two new words.
		assertEquals(2, secondCredited)
	}

	@Test
	fun `deletions do not subtract from session totals`() = runTest {
		tracker.rememberBaseline(sceneId = 1, content = "the original ten words for our chapter introduction here")
		val credited = tracker.onSceneSaved(
			sceneId = 1,
			newContent = "the original",
			source = UpdateSource.Editor,
		)
		assertEquals(0, credited)

		val log = repository.loadOwnLog()
		assertTrue(log.sessions.isEmpty())
	}

	@Test
	fun `gap past MERGE_GAP seals previous session and opens a new one`() = runTest {
		tracker.rememberBaseline(sceneId = 1, content = "")
		tracker.onSceneSaved(1, "morning words written here", UpdateSource.Editor)

		// Jump 7 hours into the future — past MERGE_GAP.
		clock.advanceTime(7.hours)

		tracker.rememberBaseline(sceneId = 1, content = "morning words written here")
		tracker.onSceneSaved(
			1,
			"morning words written here followed by evening additions",
			UpdateSource.Editor,
		)

		val log = repository.loadOwnLog()
		assertEquals(2, log.sessions.size)
		assertTrue(log.sessions.first().sealed)
		assertTrue(!log.sessions.last().sealed)
	}

	@Test
	fun `forgetBaseline drops state for deleted scenes`() = runTest {
		tracker.rememberBaseline(sceneId = 1, content = "some original text")
		tracker.forgetBaseline(1)

		// Now there's no baseline — first save establishes one and credits nothing.
		val credited = tracker.onSceneSaved(
			sceneId = 1,
			newContent = "some original text plus three more",
			source = UpdateSource.Editor,
		)
		assertEquals(0, credited)
	}

	@Test
	fun `tracker writes file under the project's writing_activity folder`() = runTest {
		tracker.rememberBaseline(sceneId = 1, content = "")
		tracker.onSceneSaved(1, "five new words written here", UpdateSource.Editor)

		val expectedPath = "/projects/Test Project/scenes/.activity/device-test.toml".toPath()
		assertTrue(fileSystem.exists(expectedPath), "Expected log file at $expectedPath")
	}
}
