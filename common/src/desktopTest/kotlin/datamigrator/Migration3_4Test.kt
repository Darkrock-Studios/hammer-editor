package datamigrator

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration3_4
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProject1Def
import io.mockk.mockk
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testProjectId
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Migration3_4Test : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var projDef: ProjectDef

	private val legacyPath: Path
		get() = projDef.path.toOkioPath() / Migration3_4.LEGACY_PROJECT_SETTINGS_FILENAME

	private val settingsPath: Path
		get() = projDef.path.toOkioPath() / ProjectDataDatasource.FILENAME

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		val projectPath = "/projects".toPath().div("Test Project")
		ffs.createDirectories(projectPath)
		projDef = ProjectDef(id = testProjectId("Test Project"), name = "Test Project", path = projectPath.toHPath())
	}

	private val metadataPath: Path
		get() = projDef.path.toOkioPath() / ProjectMetadata.FILENAME

	private val toml = createTomlSerializer()

	private fun metadataDatasource() = ProjectMetadataDatasource(ffs, toml)

	private fun migration() = Migration3_4(ffs, metadataDatasource())

	private fun read(path: Path): String = ffs.read(path) { readUtf8() }

	private fun migrator(vararg migrations: Pair<Int, Migration>) =
		object : DataMigrator(mockk(relaxed = true), mockk(relaxed = true), metadataDatasource()) {
			override fun getMigrators(): Map<Int, Migration> = migrations.toMap()
		}

	private fun writeMetadata(infoLines: String) {
		ffs.write(metadataPath) {
			writeUtf8("[info]\ncreated = 2022-12-30T07:08:02.691261600Z\ndataVersion = 3\n$infoLines")
		}
	}

	@Test
	fun `seeds the project id from the legacy server project id`() {
		writeMetadata("serverProjectId = \"$SERVER_ID\"\n")

		migrator(4 to migration()).migrateProject(projDef)

		assertEquals(ProjectId(SERVER_ID), metadataDatasource().loadMetadata(projDef).info.projectId)
		assertTrue(read(metadataPath).contains("projectId = \"$SERVER_ID\""))
		assertFalse(read(metadataPath).contains("serverProjectId"))
	}

	@Test
	fun `Test Project 1 keeps its server id as its project id`() {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProject1Def()

		migration().migrate(def)

		assertEquals(ProjectId(SERVER_ID), metadataDatasource().loadProjectId(def))
	}

	@Test
	fun `a project migrated from before version 3 keeps its server id`() {
		ffs.write(metadataPath) {
			writeUtf8(
				"[info]\ncreated = 2022-12-30T07:08:02.691261600Z\ndataVersion = 1\n" +
					"serverProjectId = \"$SERVER_ID\"\n"
			)
		}
		// The earlier steps rewrite project.toml (the version stamp) before Migration3_4 runs.
		val noOp = { version: Int ->
			object : Migration {
				override val toVersion = version
				override fun migrate(projectDef: ProjectDef) {}
			}
		}
		migrator(2 to noOp(2), 3 to noOp(3), 4 to migration()).migrateProject(projDef)

		assertEquals(4, metadataDatasource().loadMetadata(projDef).info.dataVersion)
		assertEquals(ProjectId(SERVER_ID), metadataDatasource().loadProjectId(projDef))
		assertFalse(read(metadataPath).contains("serverProjectId"))
	}

	@Test
	fun `mints a project id when there is none`() {
		writeMetadata("")

		migration().migrate(projDef)

		assertNotNull(metadataDatasource().loadMetadata(projDef).info.projectId)
	}

	@Test
	fun `keeps an existing project id over the legacy server id`() {
		writeMetadata("projectId = \"$PROJECT_ID\"\nserverProjectId = \"$SERVER_ID\"\n")

		migration().migrate(projDef)

		assertEquals(ProjectId(PROJECT_ID), metadataDatasource().loadMetadata(projDef).info.projectId)
	}

	@Test
	fun `a second run keeps the seeded project id`() {
		writeMetadata("")

		migration().migrate(projDef)
		val first = metadataDatasource().loadMetadata(projDef).info.projectId
		migration().migrate(projDef)

		assertEquals(first, metadataDatasource().loadMetadata(projDef).info.projectId)
	}

	@Test
	fun `renames project_data toml to settings toml`() {
		ffs.write(legacyPath) { writeUtf8(LEGACY_CONTENT) }

		migration().migrate(projDef)

		assertFalse(ffs.exists(legacyPath))
		assertEquals(LEGACY_CONTENT, read(settingsPath))
	}

	@Test
	fun `leaves both files when both exist`() {
		ffs.write(legacyPath) { writeUtf8(LEGACY_CONTENT) }
		ffs.write(settingsPath) { writeUtf8(CURRENT_CONTENT) }

		migration().migrate(projDef)

		assertEquals(LEGACY_CONTENT, read(legacyPath))
		assertEquals(CURRENT_CONTENT, read(settingsPath))
	}

	@Test
	fun `no-op when neither file exists`() {
		migration().migrate(projDef)

		assertFalse(ffs.exists(legacyPath))
		assertFalse(ffs.exists(settingsPath))
	}

	@Test
	fun `second run is a no-op`() {
		ffs.write(legacyPath) { writeUtf8(LEGACY_CONTENT) }

		migration().migrate(projDef)
		migration().migrate(projDef)

		assertFalse(ffs.exists(legacyPath))
		assertEquals(LEGACY_CONTENT, read(settingsPath))
	}

	private companion object {
		const val SERVER_ID = "5f1d7446-1f08-4909-a81e-cdc7470a2f63"
		const val PROJECT_ID = "0b6c1d2e-3f40-4a5b-8c6d-7e8f9a0b1c2d"
		const val LEGACY_CONTENT = "[data]\nauthorName = \"Legacy\"\n"
		const val CURRENT_CONTENT = "[data]\nauthorName = \"Current\"\n"
	}
}
