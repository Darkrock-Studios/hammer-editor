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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import repositories.writingactivity.SampleProse.Companion.paragraphs
import testProjectId
import utils.BaseTest
import utils.TestClock
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Each test states the count a writer would expect. */
class WritingActivityAccuracyTest : BaseTest() {

	private lateinit var repository: WritingActivityRepository

	private val projectDef = ProjectDef(
		id = testProjectId("Test Project"),
		name = "Test Project",
		path = "/projects/Test Project".toPath().toHPath(),
	)

	/** 01:00 local time today, so a few hours later is still today. */
	private val earlyToday: Instant = TimeZone.currentSystemDefault().let { zone ->
		Clock.System.now().toLocalDateTime(zone).date.atStartOfDayIn(zone) + 1.hours
	}
	private val clock = TestClock(object : Clock {
		override fun now(): Instant = earlyToday
	})
	private lateinit var tracker: WritingSessionTracker

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()
		val fileSystem = FakeFileSystem()
		fileSystem.createDirectories("/projects/Test Project".toPath())
		val datasource = WritingActivityDatasource(fileSystem, createTomlSerializer(), projectDef)
		val settings: GlobalSettingsStore = mockk(relaxed = true)
		coEvery { settings.ensureInstallId() } returns "device-test"
		every { settings.deviceLabelOrDefault() } returns "Test Device"
		repository = WritingActivityRepository(datasource, settings, projectDef)
		tracker = WritingSessionTracker(repository, clock, projectDef)
	}

	private suspend fun saves(vararg contents: String, scene: Int = 1): Int =
		contents.sumOf { tracker.onSceneSaved(scene, it, UpdateSource.Editor) }

	private val sceneA = SampleProse(1).words(2000)
	private val sceneB = SampleProse(2).words(2000)
	private val fresh = SampleProse(3)

	@Test
	fun `autosave landing mid-word`() = runTest {
		tracker.rememberBaseline(1, "I went home")
		val credited = saves(
			"I went home and ma",
			"I went home and made din",
			"I went home and made dinner",
		)
		assertEquals(3, credited)
	}

	@Test
	fun `autosave after every keystroke`() = runTest {
		val base = "I went home"
		val typed = " and made dinner"
		tracker.rememberBaseline(1, base)
		val credited = saves(*(1..typed.length).map { base + typed.take(it) }.toTypedArray())
		assertEquals(3, credited)
	}

	@Test
	fun `typo fixed after an autosave`() = runTest {
		tracker.rememberBaseline(1, "I went home")
		val credited = saves("I went home adn", "I went home and")
		assertEquals(1, credited)
	}

	@Test
	fun `delete a sentence then undo`() = runTest {
		val text = "I went home. The rain had stopped an hour before."
		tracker.rememberBaseline(1, text)
		val credited = saves("I went home.", text)
		assertEquals(0, credited)
	}

	@Test
	fun `cut and paste with an autosave in between`() = runTest {
		tracker.rememberBaseline(1, "Alpha beta gamma.\n\nDelta epsilon.")
		val credited = saves("Delta epsilon.", "Delta epsilon.\n\nAlpha beta gamma.")
		assertEquals(0, credited)
	}

	@Test
	fun `text arriving by sync into an open scene`() = runTest {
		tracker.rememberBaseline(1, "I went home")
		tracker.onSceneSaved(1, "I went home and made a very large dinner for everyone", UpdateSource.Sync)
		val credited = saves("I went home and made a very large dinner for everyone tonight")
		assertEquals(1, credited)
	}

	@Test
	fun `draft restored into an open scene`() = runTest {
		tracker.rememberBaseline(1, "I went home")
		tracker.onSceneSaved(1, "She stayed out all night dancing", UpdateSource.Drafts)
		val credited = saves("She stayed out all night dancing alone")
		assertEquals(1, credited)
	}

	@Test
	fun `capitalising an existing word`() = runTest {
		tracker.rememberBaseline(1, "the wonderful day")
		val credited = saves("The wonderful day")
		assertEquals(0, credited)
	}

	@Test
	fun `words typed before a re-anchor are not lost or counted again`() = runTest {
		tracker.rememberBaseline(1, "I went home")
		val beforeSync = saves("I went home and made dinner")
		tracker.onSceneSaved(1, "I went home and made dinner. The rain stopped.", UpdateSource.Sync)

		val afterSync = saves("I went home and made dinner. The rain stopped. We walked out.")

		assertEquals(3, beforeSync)
		assertEquals(3, afterSync)
	}

	@Test
	fun `moving a passage between scenes credits nothing`() = runTest {
		tracker.rememberBaseline(1, paragraphs(sceneA))
		tracker.rememberBaseline(2, paragraphs(sceneB))

		val credited = saves(paragraphs(sceneA.take(800) + sceneA.drop(1100))) +
			saves(paragraphs(sceneB + sceneA.subList(800, 1100)), scene = 2)

		assertEquals(0, credited)
	}

	@Test
	fun `trimming one scene and drafting in another credits the draft`() = runTest {
		tracker.rememberBaseline(1, paragraphs(sceneA))
		tracker.rememberBaseline(2, paragraphs(sceneB))

		val credited = saves(paragraphs(sceneA.take(800) + sceneA.drop(1100))) +
			saves(paragraphs(sceneB + fresh.words(500)), scene = 2)

		assertEquals(500, credited)
	}

	@Test
	fun `a deleted scene's text pasted elsewhere credits nothing`() = runTest {
		tracker.rememberBaseline(1, paragraphs(sceneA))
		tracker.rememberBaseline(2, paragraphs(sceneB))
		tracker.forgetBaseline(1)

		val credited = saves(paragraphs(sceneB + sceneA), scene = 2)

		assertEquals(0, credited)
	}

	@Test
	fun `words typed in a deleted scene and pasted elsewhere count once`() = runTest {
		val typed = fresh.words(200)
		tracker.rememberBaseline(1, paragraphs(sceneA))
		tracker.rememberBaseline(2, paragraphs(sceneB))
		val inA = saves(paragraphs(sceneA + typed))
		tracker.forgetBaseline(1)

		val inB = saves(paragraphs(sceneB + typed), scene = 2)

		assertEquals(200, inA)
		assertEquals(0, inB)
	}

	@Test
	fun `drafting after deleting a scene credits the draft`() = runTest {
		tracker.rememberBaseline(1, paragraphs(sceneA))
		tracker.rememberBaseline(2, paragraphs(sceneB))
		tracker.forgetBaseline(1)

		assertEquals(500, saves(paragraphs(sceneB + fresh.words(500)), scene = 2))
	}

	@Test
	fun `a new day starts a new epoch`() = runTest {
		tracker.rememberBaseline(1, "Start.")
		saves("Start. One two three four five six seven eight nine ten.", "Start.")
		clock.advanceTime(24.hours)

		assertEquals(3, saves("Start. Something else entirely."))
	}

	@Test
	fun `a long gap starts a new epoch`() = runTest {
		tracker.rememberBaseline(1, "Start.")
		saves("Start. One two three four five six seven eight nine ten.", "Start.")
		clock.advanceTime(7.hours)

		assertEquals(3, saves("Start. Something else entirely."))
	}

	@Test
	fun `a long gap since words were last recorded starts a new epoch`() = runTest {
		tracker.rememberBaseline(1, "Start.")
		saves("Start. One two three four five six seven eight nine ten.")
		repeat(6) {
			clock.advanceTime(1.hours)
			saves("Start.")
		}
		clock.advanceTime(45.minutes)

		assertEquals(3, saves("Start. Something else entirely."))
	}

	@Test
	fun `a clock set back does not start a new epoch`() = runTest {
		tracker.rememberBaseline(1, "Start.")
		saves("Start. One two three four five six seven eight nine ten.", "Start.")
		clock.advanceTime(-24.hours)

		assertEquals(0, saves("Start. Something else entirely."))
	}

	@Test
	fun `within an epoch retyping deleted words adds nothing`() = runTest {
		tracker.rememberBaseline(1, "Start.")
		saves("Start. One two three four five six seven eight nine ten.", "Start.")
		clock.advanceTime(5.hours)

		assertEquals(0, saves("Start. Something else entirely."))
	}

	@Test
	fun `text from the previous epoch moved today credits nothing`() = runTest {
		val typed = fresh.words(100)
		tracker.rememberBaseline(1, paragraphs(sceneA))
		saves(paragraphs(sceneA + typed))
		clock.advanceTime(24.hours)

		assertEquals(0, saves(paragraphs(typed + sceneA)))
	}

	@Test
	fun `deleting a scene after a new epoch keeps the new epoch's words`() = runTest {
		tracker.rememberBaseline(1, paragraphs(sceneA))
		tracker.rememberBaseline(2, paragraphs(sceneB))
		saves(paragraphs(sceneA + fresh.words(300)))
		clock.advanceTime(24.hours)
		val drafted = sceneB + fresh.words(50)
		val newEpoch = saves(paragraphs(drafted), scene = 2)

		tracker.forgetBaseline(1)
		val afterDelete = saves(paragraphs(drafted + fresh.words(10)), scene = 2)

		assertEquals(50, newEpoch)
		assertEquals(10, afterDelete)
	}
}
