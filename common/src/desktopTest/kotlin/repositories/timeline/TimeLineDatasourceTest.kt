package repositories.timeline

import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import getProject1Def
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions.assertTrue
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
}
