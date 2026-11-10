package repositories

import com.darkrockstudios.apps.hammer.base.http.readTomlOrNull
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.migrator.PROJECT_DATA_VERSION
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.util.zip.unzipBytesToDirectory
import kotlinx.serialization.encodeToString
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testProjectId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bundled example ships in the current on-disk format: opening it runs no migration, and every
 * file is byte for byte what the app's own serializers write, so nothing is left for the app to
 * backfill on a later save (and so re-upload after a sync).
 */
class ExampleProjectFormatTest {

	private val toml = createTomlSerializer()
	private val projectsDir = "/projects".toPath()
	private val projectDir = projectsDir / ExampleProjectRepository.PROJECT_NAME
	private val projDef = ProjectDef(
		id = testProjectId(ExampleProjectRepository.PROJECT_NAME),
		name = ExampleProjectRepository.PROJECT_NAME,
		path = projectDir.toHPath(),
	)
	private lateinit var ffs: FakeFileSystem

	@BeforeEach
	fun setup() {
		ffs = FakeFileSystem()
		val zipBytes = ExampleProjectRepository::class.java
			.getResourceAsStream("/raw/${ExampleProjectRepository.EXAMPLE_PROJECT_FILE_NAME}")!!
			.use { it.readBytes() }
		unzipBytesToDirectory(ffs, zipBytes, projectsDir)
	}

	private fun text(path: Path): String = ffs.read(path) { readUtf8() }

	/** Decodes [path] and checks the app would write the decoded value back as exactly the same text. */
	private inline fun <reified T : Any> assertWrittenByApp(path: Path, header: String? = null): T {
		val decoded = assertNotNull(ffs.readTomlOrNull<T>(path, toml) { throw it }, path.toString())
		val expected = (header?.let { "$it\n" } ?: "") + toml.encodeToString(decoded)
		assertEquals(expected, text(path), path.toString())
		return decoded
	}

	private fun sceneIdsOnDisk(): List<Int> {
		val scenesDir = projectDir / SceneDatasource.SCENE_DIRECTORY
		return ffs.listRecursively(scenesDir)
			.filter { path -> path.relativeTo(scenesDir).segments.none { it.startsWith(".") } }
			.mapNotNull { SceneDatasource.SCENE_FILENAME_PATTERN.matchEntire(it.name) }
			.filter { it.groupValues[4] == ".md" }
			.map { it.groupValues[3].toInt() }
			.toList()
	}

	@Test
	fun `project metadata is current and leaves only the id to be minted`() {
		val metadata = assertWrittenByApp<ProjectMetadata>(
			projectDir / ProjectMetadata.FILENAME,
			header = ProjectMetadataDatasource.FILE_HEADER,
		)

		assertEquals(PROJECT_DATA_VERSION, metadata.info.dataVersion)
		assertNotNull(metadata.info.lastAccessed)
		assertNull(metadata.info.projectId, "every install mints its own id")
	}

	@Test
	fun `settings are as the app writes them`() {
		val stored = assertWrittenByApp<StoredProjectData>(
			projectDir / ProjectDataDatasource.FILENAME,
			header = ProjectDataDatasource.FILE_HEADER,
		)

		assertNull(stored.lastSyncedHash, "a bundle has never been synced")
	}

	@Test
	fun `every scene has complete metadata with fixed timestamps`() {
		val sceneIds = sceneIdsOnDisk()
		val metadataDir = SceneMetadataDatasource.getMetadataDirectory(projDef, ffs).toOkioPath()
		val projectCreated = assertNotNull(
			ffs.readTomlOrNull<ProjectMetadata>(projectDir / ProjectMetadata.FILENAME, toml)
		).info.created

		assertEquals(17, sceneIds.size)
		assertEquals(
			sceneIds.mapTo(HashSet()) { SceneMetadataDatasource.getMetadataFilenameFromId(it) },
			ffs.list(metadataDir).mapTo(HashSet()) { it.name },
			"one metadata file per scene and no strays",
		)

		sceneIds.forEach { sceneId ->
			val path = metadataDir / SceneMetadataDatasource.getMetadataFilenameFromId(sceneId)
			val metadata = assertWrittenByApp<SceneMetadata>(path)
			val created = assertNotNull(metadata.created, "scene $sceneId has no created")
			val lastEdited = assertNotNull(metadata.lastEdited, "scene $sceneId has no lastEdited")
			assertTrue(created >= projectCreated, "scene $sceneId was created before the project")
			assertTrue(lastEdited >= created, "scene $sceneId was edited before it was created")
		}
	}

	@Test
	fun `encyclopedia entries sit in their type directory under their own filename`() {
		val entries = ffs.listRecursively(projectDir / EncyclopediaDatasource.ENCYCLOPEDIA_DIRECTORY)
			.filter { it.name.endsWith(".toml") }
			.toList()

		assertTrue(entries.isNotEmpty())
		entries.forEach { path ->
			val container = assertWrittenByApp<EntryContainer>(path)
			assertEquals(container.entry.type.text, path.parent!!.name, path.toString())
			assertEquals(EncyclopediaDatasource.getEntryFilename(container.entry), path.name)
		}
	}

	@Test
	fun `notes are as the app writes them`() {
		val notes = ffs.list(projectDir / NotesDatasource.NOTES_DIRECTORY)

		assertTrue(notes.isNotEmpty())
		notes.forEach { path ->
			val note = assertWrittenByApp<NoteContainer>(path)
			assertEquals(NotesDatasource.getNoteFilenameFromId(note.note.id), path.name)
		}
	}

	@Test
	fun `timeline is as the app writes it`() {
		val path = TimeLineDatasource.getTimelineFilePath(projDef)
		val timeline = TimeLineDatasource.loadTimeline(path, ffs, toml)
		val rewritten = "/rewritten/timeline.toml".toPath()
		ffs.createDirectories(rewritten.parent!!)
		TimeLineDatasource.storeTimeline(rewritten.toHPath(), ffs, toml, timeline)

		assertTrue(timeline.events.isNotEmpty())
		assertEquals(text(rewritten), text(path.toOkioPath()))
	}
}
