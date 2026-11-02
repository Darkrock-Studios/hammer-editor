import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.projectsync.DataChoice
import com.darkrockstudios.apps.hammer.common.projectsync.TimelineSummaryLabels
import com.darkrockstudios.apps.hammer.common.projectsync.buildResolved
import com.darkrockstudios.apps.hammer.common.projectsync.timelinesSummary
import org.junit.Test
import kotlin.test.assertEquals

class ProjectDataConflictResolveTest {

	private val localMain = TimelineDef.DEFAULT.copy(
		mode = TimelineMode.DATED,
		calendar = CalendarDef(CalendarPreset.STORY, epochName = "Landing"),
		laneOrder = listOf("Alice", "Bob"),
	)
	private val serverMain = TimelineDef.DEFAULT.copy(laneOrder = listOf("Carol"))
	private val serverOnly = TimelineDef(id = "b2c3d4e5", name = "Second")

	private val local = ProjectData(
		authorName = "Local",
		coverImageId = 1,
		dictionaryWords = setOf("a"),
		timelines = listOf(localMain),
	)
	private val server = ProjectData(
		authorName = "Server",
		coverImageId = null,
		dictionaryWords = setOf("b"),
		timelines = listOf(serverMain, serverOnly),
	)

	private fun resolve(
		author: DataChoice = DataChoice.LOCAL,
		cover: DataChoice = DataChoice.LOCAL,
		timelines: DataChoice = DataChoice.LOCAL,
	) = buildResolved(
		local = local,
		server = server,
		authorChoice = author,
		themeChoice = DataChoice.LOCAL,
		goalChoice = DataChoice.LOCAL,
		tagsChoice = DataChoice.LOCAL,
		languageChoice = DataChoice.LOCAL,
		dictionaryChoice = DataChoice.LOCAL,
		coverChoice = cover,
		timelinesChoice = timelines,
	)

	@Test
	fun `cover follows its own choice`() {
		assertEquals(1, resolve(author = DataChoice.REMOTE, cover = DataChoice.LOCAL).coverImageId)
		assertEquals(null, resolve(author = DataChoice.LOCAL, cover = DataChoice.REMOTE).coverImageId)
	}

	@Test
	fun `other fields are independent of the cover choice`() {
		val resolved = resolve(author = DataChoice.REMOTE, cover = DataChoice.LOCAL)
		assertEquals("Server", resolved.authorName)
		assertEquals(setOf("a", "b"), resolved.dictionaryWords)
	}

	@Test
	fun `timelines follow their own choice`() {
		val keepLocal = resolve(author = DataChoice.REMOTE, timelines = DataChoice.LOCAL)
		val takeServer = resolve(author = DataChoice.LOCAL, timelines = DataChoice.REMOTE)

		assertEquals(localMain, keepLocal.timelines.single { it.id == TimelineDef.MAIN_ID })
		assertEquals("Server", keepLocal.authorName)
		assertEquals(serverMain, takeServer.timelines.single { it.id == TimelineDef.MAIN_ID })
		assertEquals("Local", takeServer.authorName)
	}

	@Test
	fun `a timeline only one side has survives either choice`() {
		assertEquals(listOf(localMain, serverOnly), resolve(timelines = DataChoice.LOCAL).timelines)
		assertEquals(listOf(serverMain, serverOnly), resolve(timelines = DataChoice.REMOTE).timelines)
	}

	@Test
	fun `timeline summary shows name, mode, calendar and lane count`() {
		val labels = TimelineSummaryLabels(
			unset = "(not set)",
			manual = "manual",
			dated = "dated",
			storyCalendar = "Story calendar",
			gregorianCalendar = "Gregorian calendar",
			coarsestUnit = { "from $it" },
			lanes = { "$it lanes" },
		)
		val hourScale = localMain.copy(calendar = CalendarDef(coarsestUnit = "hour"), laneOrder = emptyList())

		assertEquals("Main · dated · Story calendar (Landing) · 2 lanes", timelinesSummary(listOf(localMain), labels))
		assertEquals("Main · manual · 1 lanes\nSecond · manual", timelinesSummary(listOf(serverMain, serverOnly), labels))
		assertEquals("Main · dated · Story calendar · from hour", timelinesSummary(listOf(hourScale), labels))
		assertEquals("(not set)", timelinesSummary(emptyList(), labels))
	}
}
