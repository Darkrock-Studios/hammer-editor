package repositories.projectmetadata

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.ProjectMetadataDatasourceOkio
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProject1Def
import kotlinx.datetime.Instant
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import utils.BaseTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectMetadataDatasourceTest : BaseTest() {

	private lateinit var fileSystem: FakeFileSystem

	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	private lateinit var toml: Toml

	@Before
	override fun setup() {
		super.setup()
		fileSystem = FakeFileSystem()

		toml = createTomlSerializer()

		projectMetadataDatasource = ProjectMetadataDatasourceOkio(
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
		assertEquals(expectedMetadata, metadata)
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