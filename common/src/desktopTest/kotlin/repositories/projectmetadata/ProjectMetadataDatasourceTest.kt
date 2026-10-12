package repositories.projectmetadata

import PROJECT_1_NAME
import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProject1Def
import getProjectDef
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProjectMetadataDatasourceTest : BaseTest() {

	private lateinit var fileSystem: FakeFileSystem

	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	private lateinit var toml: Toml

	@BeforeEach
	override fun setup() {
		super.setup()
		fileSystem = FakeFileSystem()

		toml = createTomlSerializer()

		projectMetadataDatasource = ProjectMetadataDatasource(
			fileSystem = fileSystem,
			toml = toml,
		)
	}

	@Test
	fun `Load Project Metadata`() {
		createProject(fileSystem, PROJECT_1_NAME)
		val metadata = projectMetadataDatasource.loadMetadata(getProject1Def())

		val expectedMetadata = ProjectMetadata(
			Info(
				created = Instant.parse("2022-12-30T07:08:02.691261600Z"),
				lastAccessed = Instant.parse("2024-06-28T05:09:10.649054300Z"),
				dataVersion = 1,
			)
		)
		// A plain read: the legacy serverProjectId is left for Migration3_4, not healed here.
		assertEquals(expectedMetadata, metadata)
	}

	@Test
	fun `loadProjectId returns the stored id`() {
		createProject(fileSystem, PROJECT_2_NAME)

		val projectId = projectMetadataDatasource.loadProjectId(getProjectDef(PROJECT_2_NAME))

		assertEquals(ProjectId("8c3f6a2e-4b1d-4e5a-9f7c-2d6b1a0e9c43"), projectId)
	}

	@Test
	fun `loadProjectId mints and persists an id when the file has none`() {
		createProject(fileSystem, PROJECT_1_NAME)

		val minted = projectMetadataDatasource.loadProjectId(getProject1Def())

		assertEquals(minted, projectMetadataDatasource.loadMetadata(getProject1Def()).info.projectId)
		assertEquals(minted, projectMetadataDatasource.loadProjectId(getProject1Def()))
		// The rest of the metadata survives the heal.
		assertEquals(1, projectMetadataDatasource.loadMetadata(getProject1Def()).info.dataVersion)
	}

	@Test
	fun `Corrupt project metadata recovers instead of throwing`() {
		createProject(fileSystem, PROJECT_1_NAME)
		val path = projectMetadataDatasource.getMetadataPath(getProject1Def()).toOkioPath()
		// dataVersion is an Int; a non-numeric value throws NumberFormatException on decode,
		// which is not a SerializationException. Loading must recover, not crash the project load.
		fileSystem.write(path) {
			writeUtf8(
				"""
				[info]
				created = "2022-12-30T07:08:02.691261600Z"
				dataVersion = "not-a-number"
				""".trimIndent()
			)
		}

		val metadata = projectMetadataDatasource.loadMetadata(getProject1Def())

		// Recreated fresh (dataVersion 0 so migrators re-run) rather than throwing.
		assertEquals(0, metadata.info.dataVersion)
		assertNotNull(metadata.info.projectId)
		assertEquals(metadata.info.projectId, projectMetadataDatasource.loadProjectId(getProject1Def()))
	}

	@Test
	fun `Missing project metadata is recreated with a fresh id`() {
		createProject(fileSystem, PROJECT_1_NAME)
		val path = projectMetadataDatasource.getMetadataPath(getProject1Def()).toOkioPath()
		fileSystem.delete(path)

		val projectId = projectMetadataDatasource.loadProjectId(getProject1Def())

		assertTrue(fileSystem.exists(path))
		assertEquals(projectId, projectMetadataDatasource.loadProjectId(getProject1Def()))
	}

	@Test
	fun `Get Project Metadata Path`() {
		createProject(fileSystem, PROJECT_1_NAME)
		val path = projectMetadataDatasource.getMetadataPath(getProject1Def()).toOkioPath()
		assertTrue(path.segments.size >= 3)
		path.segments.reversed().forEachIndexed { i, segment ->
			when (i) {
				2 -> assertEquals("HammerProjects", segment)
				1 -> assertEquals("Test Project 1", segment)
				0 -> assertEquals("project.toml", segment)
			}
		}
	}

	@Test
	fun `Save Project Metadata`() {
		createProject(fileSystem, PROJECT_1_NAME)

		val newMetadata = ProjectMetadata(
			Info(
				created = Instant.parse("2025-01-30T07:08:02.691261600Z"),
				lastAccessed = Instant.parse("2026-02-28T05:09:10.649054300Z"),
				dataVersion = 2,
			)
		)

		projectMetadataDatasource.saveMetadata(
			newMetadata,
			getProject1Def()
		)

		val path = projectMetadataDatasource.getMetadataPath(getProject1Def()).toOkioPath()
		val protectMetadata: ProjectMetadata = fileSystem.readToml(path, toml)

		assertEquals(newMetadata, protectMetadata)
	}

	@Test
	fun `Saved metadata starts with the header and round-trips`() {
		createProject(fileSystem, PROJECT_1_NAME)
		val metadata = ProjectMetadata(
			Info(
				created = Instant.parse("2025-01-30T07:08:02.691261600Z"),
				dataVersion = 4,
				projectId = ProjectId("5f1d7446-1f08-4909-a81e-cdc7470a2f63"),
			)
		)

		projectMetadataDatasource.saveMetadata(metadata, getProject1Def())

		val path = projectMetadataDatasource.getMetadataPath(getProject1Def()).toOkioPath()
		val text = fileSystem.read(path) { readUtf8() }
		assertTrue(text.startsWith(ProjectMetadataDatasource.FILE_HEADER + "\n"))
		assertEquals(metadata, projectMetadataDatasource.loadMetadata(getProject1Def()))
	}

	@Test
	fun `Update Project Metadata`() {
		createProject(fileSystem, PROJECT_1_NAME)

		lateinit var updatedMetadata: ProjectMetadata
		projectMetadataDatasource.updateMetadata(getProject1Def()) { meta ->
			updatedMetadata = meta.copy(info = meta.info.copy(dataVersion = 2))
			updatedMetadata
		}

		val path = projectMetadataDatasource.getMetadataPath(getProject1Def()).toOkioPath()
		val protectMetadata: ProjectMetadata = fileSystem.readToml(path, toml)

		assertEquals(updatedMetadata, protectMetadata)
	}
}