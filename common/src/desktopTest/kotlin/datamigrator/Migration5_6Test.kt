package datamigrator

import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration5_6
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.toApiEntity
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Sink
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class Migration5_6Test : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private val toml = createTomlSerializer()

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
	}

	private fun migrate(projDef: ProjectDef, fileSystem: FileSystem = ffs) =
		Migration5_6(fileSystem, toml).migrate(projDef)

	private fun timelinePath(projDef: ProjectDef): Path =
		TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()

	private fun loadEvents(projDef: ProjectDef): List<TimeLineEvent> {
		val container = TimeLineDatasource.loadTimelineOrNull(TimeLineDatasource.getTimelineFilePath(projDef), ffs, toml)
		return assertNotNull(container).events
	}

	private fun readBytes(projDef: ProjectDef) = ffs.read(timelinePath(projDef)) { readByteString() }

	@Test
	fun `Renames date to label and keeps every other field`() {
		val projDef = getProjectDef(MIGRATION_5_6)
		createProject(ffs, MIGRATION_5_6)

		migrate(projDef)

		assertEquals(
			listOf(
				TimeLineEvent(
					id = 3,
					order = 0,
					label = "Sep 1, 1865",
					content = "Alice follows the White Rabbit.",
					tags = setOf("opening", "white-rabbit"),
				),
				TimeLineEvent(id = 7, order = 2, label = null, content = "An undated event.\n\nWith a second paragraph."),
				TimeLineEvent(id = 5, order = 1, label = "  Teatime  ", content = "The Mad Tea Party."),
			),
			loadEvents(projDef),
		)
		assertFalse(ffs.read(timelinePath(projDef)) { readUtf8() }.contains("date ="))
	}

	@Test
	fun `Every event keeps its hash`() {
		val projDef = getProjectDef(MIGRATION_5_6)
		createProject(ffs, MIGRATION_5_6)
		val legacy = toml.decodeFromString(
			LegacyTimeline.serializer(),
			ffs.read(timelinePath(projDef)) { readUtf8() },
		).events
		val before = legacy.associate { event ->
			event.id to EntityHasher.hashTimelineEvent(event.id, event.order, event.content, event.date, event.tags)
		}

		migrate(projDef)

		val after = loadEvents(projDef).associate { it.id to it.toApiEntity().hash() }
		assertEquals(3, before.size)
		assertEquals(before, after)
	}

	@Test
	fun `Second run writes nothing`() {
		val projDef = getProjectDef(MIGRATION_5_6)
		createProject(ffs, MIGRATION_5_6)
		migrate(projDef)
		val migrated = readBytes(projDef)

		val recording = WriteRecordingFileSystem(ffs)
		migrate(projDef, recording)

		assertEquals(emptyList(), recording.writes)
		assertEquals(migrated, readBytes(projDef))
	}

	@Test
	fun `Timeline already in the new format is left alone`() {
		val projDef = getProjectDef(MIGRATION_5_6_ALREADY)
		createProject(ffs, MIGRATION_5_6_ALREADY)
		val original = readBytes(projDef)

		val recording = WriteRecordingFileSystem(ffs)
		migrate(projDef, recording)

		assertEquals(emptyList(), recording.writes)
		assertEquals(original, readBytes(projDef))
		val events = loadEvents(projDef)
		assertEquals(StoryDate(mapOf("year" to 97L, "day" to 210L)), events.first { it.id == 4 }.start)
		assertEquals(TimelineKind.SCENE, events.first { it.id == 9 }.kind)
	}

	@Test
	fun `Missing timeline is skipped`() {
		val projDef = getProjectDef(MIGRATION_5_6)
		ffs.createDirectories(projDef.path.toOkioPath())

		migrate(projDef)

		assertFalse(ffs.exists(timelinePath(projDef)))
		assertFalse(ffs.exists(TimeLineDatasource.getTimelineDir(projDef).toOkioPath()))
	}

	@Test
	fun `Corrupt timeline is skipped without throwing`() {
		val projDef = getProjectDef(MIGRATION_5_6)
		val path = timelinePath(projDef)
		ffs.createDirectories(path.parent!!)
		ffs.write(path) { writeUtf8("[[events]]\nid = \"not a number\"\ndate = @@@") }
		val original = readBytes(projDef)

		migrate(projDef)

		assertEquals(original, readBytes(projDef))
	}

	@Serializable
	private data class LegacyTimeline(val events: List<LegacyEvent>)

	@Serializable
	private data class LegacyEvent(
		val id: Int,
		val order: Int,
		val date: String? = null,
		val content: String,
		val tags: Set<String> = emptySet(),
	)

	private class WriteRecordingFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {
		val writes = mutableListOf<Path>()

		override fun sink(file: Path, mustCreate: Boolean): Sink {
			writes += file
			return super.sink(file, mustCreate)
		}

		override fun appendingSink(file: Path, mustExist: Boolean): Sink {
			writes += file
			return super.appendingSink(file, mustExist)
		}
	}

	private companion object {
		const val MIGRATION_5_6 = "Migration 5 to 6"
		const val MIGRATION_5_6_ALREADY = "Migration 5 to 6 Already"
	}
}
