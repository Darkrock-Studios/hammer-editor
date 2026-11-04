package repositories.references

import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.data.references.TimelineReferenceRemapper
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import createProject
import getProjectDef
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import repositories.timeline.writeEventsToDisk
import utils.BaseTest
import kotlin.test.assertEquals

class TimelineReferenceRemapperTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		setupKoin(module { single<SyncJournal> { mockk(relaxed = true) } })
	}

	@Test
	fun `remap rewrites placements and references on every timeline and keeps their timeline`() = runTest {
		val toml = createTomlSerializer()
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val events = listOf(
			TimeLineEvent(id = 20, order = 0, kind = TimelineKind.SCENE, subject = 4, cachedTitle = "Dinner"),
			TimeLineEvent(id = 30, order = 0, kind = TimelineKind.SCENE, subject = 4, cachedTitle = "Dinner", timeline = OTHER),
			TimeLineEvent(id = 31, order = 1, title = "Before", references = setOf(4, 7), timeline = OTHER),
			TimeLineEvent(id = 32, order = 2, kind = TimelineKind.ENTRY, subject = 4, timeline = OTHER),
		)
		writeEventsToDisk(projDef, events, ffs, toml)
		val repo = TimeLineRepository(projDef, mockk(), TimeLineDatasource(ffs, toml)).initialize()
		advanceUntilIdle()

		val rewritten = TimelineReferenceRemapper(repo).remap(oldId = 4, newId = 9, subjectKind = TimelineKind.SCENE)

		assertEquals(setOf(20, 30, 31), rewritten)
		val stored = repo.loadTimeline().events.associateBy { it.id }
		assertEquals(events[0].copy(subject = 9), stored[20])
		assertEquals(events[1].copy(subject = 9), stored[30])
		assertEquals(events[2].copy(references = setOf(9, 7)), stored[31])
		assertEquals(events[3], stored[32], "an entry placement does not follow a scene")
	}

	private companion object {
		const val OTHER = "a3f09c1e"
	}
}
