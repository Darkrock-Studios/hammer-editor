import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.projectsync.DataChoice
import com.darkrockstudios.apps.hammer.common.projectsync.TimelineSummaryLabels
import com.darkrockstudios.apps.hammer.common.projectsync.buildResolved
import com.darkrockstudios.apps.hammer.common.projectsync.timelinesDeletedOnlyBy
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

	private val labels = TimelineSummaryLabels(
		unset = "(not set)",
		manual = "manual",
		dated = "dated",
		storyCalendar = "Story calendar",
		gregorianCalendar = "Gregorian calendar",
		coarsestUnit = { "from $it" },
		lanes = { "$it lanes" },
		deleted = { "$it timelines deleted" },
	)

	@Test
	fun `timeline summary shows name, mode, calendar and lane count`() {
		val hourScale = localMain.copy(calendar = CalendarDef(coarsestUnit = "hour"), laneOrder = emptyList())

		assertEquals("Main · dated · Story calendar (Landing) · 2 lanes", timelinesSummary(listOf(localMain), 0, labels))
		assertEquals("Main · manual · 1 lanes\nSecond · manual", timelinesSummary(listOf(serverMain, serverOnly), 0, labels))
		assertEquals("Main · dated · Story calendar · from hour", timelinesSummary(listOf(hourScale), 0, labels))
		assertEquals("(not set)", timelinesSummary(emptyList(), 0, labels))
	}

	@Test
	fun `resolved data carries the union of tombstones and drops deleted defs`() {
		val deletedHere = TimelineDef(id = "c3d4e5f6", name = "Third")
		val withTombstones = { localChoice: DataChoice ->
			buildResolved(
				local = local.copy(timelines = listOf(localMain, serverOnly), deletedTimelines = setOf(deletedHere.id)),
				server = server.copy(
					timelines = listOf(serverMain, deletedHere),
					deletedTimelines = setOf(serverOnly.id, TimelineDef.MAIN_ID),
				),
				authorChoice = DataChoice.LOCAL,
				themeChoice = DataChoice.LOCAL,
				goalChoice = DataChoice.LOCAL,
				tagsChoice = DataChoice.LOCAL,
				languageChoice = DataChoice.LOCAL,
				dictionaryChoice = DataChoice.LOCAL,
				coverChoice = DataChoice.LOCAL,
				timelinesChoice = localChoice,
			)
		}

		val keepLocal = withTombstones(DataChoice.LOCAL)
		val takeServer = withTombstones(DataChoice.REMOTE)

		assertEquals(listOf(localMain), keepLocal.timelines)
		assertEquals(listOf(serverMain), takeServer.timelines)
		assertEquals(setOf(deletedHere.id, serverOnly.id), keepLocal.deletedTimelines)
		assertEquals(setOf(deletedHere.id, serverOnly.id), takeServer.deletedTimelines)
	}

	@Test
	fun `timeline summary adds a line for timelines only this side deleted`() {
		val deleter = ProjectData(timelines = listOf(serverMain), deletedTimelines = setOf("aaaaaaaa", "bbbbbbbb"))
		val other = ProjectData(timelines = listOf(serverMain), deletedTimelines = setOf("aaaaaaaa"))

		assertEquals(1, timelinesDeletedOnlyBy(deleter, other))
		assertEquals(0, timelinesDeletedOnlyBy(other, deleter))
		assertEquals(0, timelinesDeletedOnlyBy(ProjectData(deletedTimelines = setOf(TimelineDef.MAIN_ID)), other))
		assertEquals(
			"Main · manual · 1 lanes\n1 timelines deleted",
			timelinesSummary(deleter.timelines, timelinesDeletedOnlyBy(deleter, other), labels),
		)
		assertEquals("(not set)\n2 timelines deleted", timelinesSummary(emptyList(), 2, labels))
	}
}
