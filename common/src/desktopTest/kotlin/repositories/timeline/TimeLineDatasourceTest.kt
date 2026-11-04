package repositories.timeline

import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.timelineId
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import getProject1Def
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals

class TimeLineDatasourceTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml
	lateinit var datasource: TimeLineDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		datasource = TimeLineDatasource(ffs, toml)
		setupKoin(module {
			single { ffs }
			single { toml }
		})
	}

	@Test
	fun `Load Timeline`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		val events = fakeEvents()
		writeEventsToDisk(projDef, events, ffs, toml)

		val timeline = datasource.loadTimeline(projDef)
		assertEquals(events, timeline.events)
	}

	@Test
	fun `Load Timeline when none exists`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		val timeline = datasource.loadTimeline(projDef)
		assertEquals(emptyList(), timeline.events)
	}

	@Test
	fun `Malformed Timeline loads as empty`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		val file = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		ffs.createDirectories(file.parent!!)
		ffs.write(file) {
			writeUtf8("this is not valid timeline toml @@@")
		}

		val timeline = datasource.loadTimeline(projDef)

		assertEquals(emptyList(), timeline.events)
	}

	@Test
	fun `Store Timeline`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		val events = fakeEvents()

		datasource.storeTimeline(
			TimeLineContainer(events),
			projDef
		)

		val filePath = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		assertTrue(ffs.exists(filePath))

		val loadedContainer: TimeLineContainer = ffs.readToml(filePath, toml)
		assertEquals(events, loadedContainer.events)
	}

	@Test
	fun `Round trip keeps every Timeline 2 field`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		val events = listOf(
			TimeLineEvent(
				id = 4,
				order = 0,
				title = "The storm",
				label = "Late autumn",
				content = "Wind takes the roof.\nSecond line.",
				tags = setOf("weather"),
				references = setOf(12, 31),
				lane = "The Keep",
				milestone = true,
				start = StoryDate(mapOf("year" to 97L, "day" to 210L)),
				end = StoryDate(mapOf("year" to 97L, "day" to 212L, "hour" to 6L)),
			),
			TimeLineEvent(
				id = 9,
				order = 1,
				kind = TimelineKind.SCENE,
				subject = 31,
				lane = "Alice",
				cachedTitle = "Landfall",
				start = StoryDate(mapOf("year" to -3L, "day" to 214L, "hour" to 6L)),
			),
			TimeLineEvent(
				id = 10,
				order = 2,
				kind = TimelineKind.ENTRY,
				subject = 34,
				start = StoryDate(mapOf("year" to 97L)),
				end = StoryDate(mapOf("year" to 98L)),
			),
		)

		TimeLineDatasource.storeTimeline(
			TimeLineDatasource.getTimelineFilePath(projDef).also {
				ffs.createDirectories(it.toOkioPath().parent!!)
			},
			ffs,
			toml,
			TimeLineContainer(events),
		)

		val timeline = datasource.loadTimeline(projDef)
		assertEquals(events, timeline.events)
	}

	@Test
	fun `Plain events store only the fields they set`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		val events = listOf(
			TimeLineEvent(id = 0, order = 0, content = "Event 0", tags = setOf("opening")),
			TimeLineEvent(id = 1, order = 1, content = "Event 1"),
		)

		datasource.storeTimeline(TimeLineContainer(events), projDef)

		val text = readTimelineText(projDef)
		assertFalse(text.contains("null"))
		assertEquals(
			listOf(setOf("id", "order", "content", "tags"), setOf("id", "order", "content")),
			eventKeys(text),
		)
		assertEquals(events, datasource.loadTimeline(projDef).events)
	}

	@Test
	fun `Fully populated event round trips`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		val event = TimeLineEvent(
			id = 9,
			order = 3,
			kind = TimelineKind.SCENE,
			subject = 31,
			title = "Landfall",
			label = "Dawn",
			content = "They reach the shore.",
			tags = setOf("sea"),
			references = setOf(12),
			lane = "Alice",
			milestone = true,
			start = StoryDate(mapOf("year" to 97L, "day" to 214L)),
			end = StoryDate(mapOf("year" to 97L, "day" to 215L)),
			cachedTitle = "Landfall",
		)

		datasource.storeTimeline(TimeLineContainer(listOf(event)), projDef)

		assertEquals(listOf(event), datasource.loadTimeline(projDef).events)
	}

	@Test
	fun `Timeline with explicit nulls still loads`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		val file = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		ffs.createDirectories(file.parent!!)
		ffs.write(file) {
			writeUtf8(
				"""
				[[events]]
				id = 0
				order = 0
				kind = "event"
				subject = null
				title = null
				label = null
				content = "Event 0"
				tags = [ ]
				references = [ ]
				lane = null
				milestone = false
				start = null
				end = null
				cachedTitle = null
				""".trimIndent()
			)
		}

		val timeline = datasource.loadTimeline(projDef)

		assertEquals(listOf(TimeLineEvent(id = 0, order = 0, content = "Event 0")), timeline.events)
	}

	@Test
	fun `Legacy timeline with date keys still loads`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		val file = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		ffs.createDirectories(file.parent!!)
		ffs.write(file) {
			writeUtf8(
				"""
				[[events]]
				id = 0
				order = 0
				date = "First date"
				content = "Event 0"
				""".trimIndent()
			)
		}

		val timeline = datasource.loadTimeline(projDef)

		val event = timeline.events.single()
		assertEquals(0, event.id)
		assertEquals("Event 0", event.content)
		assertNull(event.label)
	}

	@Test
	fun `Legacy event without a timeline key reads as main`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		val file = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		ffs.createDirectories(file.parent!!)
		ffs.write(file) {
			writeUtf8(
				"""
				[[events]]
				id = 30
				order = 0
				kind = "scene"
				subject = 4
				cachedTitle = "Dinner"
				""".trimIndent()
			)
		}

		val event = datasource.loadTimeline(projDef).events.single()

		assertNull(event.timeline)
		assertEquals(TimelineDef.MAIN_ID, event.timelineId)
	}

	@Test
	fun `Timeline id round trips and is left out for main`() = runTest(mainTestDispatcher) {
		val projDef = getProject1Def()
		val events = listOf(
			TimeLineEvent(id = 30, order = 0, kind = TimelineKind.SCENE, subject = 4, cachedTitle = "Dinner"),
			TimeLineEvent(
				id = 71,
				order = 11,
				kind = TimelineKind.SCENE,
				subject = 4,
				cachedTitle = "Dinner",
				timeline = "a3f09c1e",
			),
		)

		datasource.storeTimeline(TimeLineContainer(events), projDef)

		val text = readTimelineText(projDef)
		val keys = eventKeys(text)
		assertFalse("timeline" in keys[0])
		assertTrue("timeline" in keys[1])
		assertTrue(text.contains("timeline = \"a3f09c1e\""))
		val loaded = datasource.loadTimeline(projDef).events
		assertEquals(events, loaded)
		assertEquals(listOf(TimelineDef.MAIN_ID, "a3f09c1e"), loaded.map { it.timelineId })
	}

	private fun readTimelineText(projDef: ProjectDef): String =
		ffs.read(TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()) { readUtf8() }

	private fun eventKeys(text: String): List<Set<String>> =
		text.split("[[events]]").drop(1).map { block ->
			block.lines().mapNotNull { KEY_LINE.find(it)?.groupValues?.get(1) }.toSet()
		}

	private companion object {
		val KEY_LINE = Regex("""^\s*(\w+)\s*=""")
	}
}
