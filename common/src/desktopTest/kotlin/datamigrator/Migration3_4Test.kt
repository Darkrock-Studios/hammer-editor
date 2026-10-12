package datamigrator

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration3_4
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
		projDef = ProjectDef(name = "Test Project", path = projectPath.toHPath())
	}

	private fun read(path: Path): String = ffs.read(path) { readUtf8() }

	@Test
	fun `renames project_data toml to project_settings toml`() {
		ffs.write(legacyPath) { writeUtf8(LEGACY_CONTENT) }

		Migration3_4(ffs).migrate(projDef)

		assertFalse(ffs.exists(legacyPath))
		assertEquals(LEGACY_CONTENT, read(settingsPath))
	}

	@Test
	fun `leaves both files when both exist`() {
		ffs.write(legacyPath) { writeUtf8(LEGACY_CONTENT) }
		ffs.write(settingsPath) { writeUtf8(CURRENT_CONTENT) }

		Migration3_4(ffs).migrate(projDef)

		assertEquals(LEGACY_CONTENT, read(legacyPath))
		assertEquals(CURRENT_CONTENT, read(settingsPath))
	}

	@Test
	fun `no-op when neither file exists`() {
		Migration3_4(ffs).migrate(projDef)

		assertFalse(ffs.exists(legacyPath))
		assertFalse(ffs.exists(settingsPath))
	}

	@Test
	fun `second run is a no-op`() {
		ffs.write(legacyPath) { writeUtf8(LEGACY_CONTENT) }

		Migration3_4(ffs).migrate(projDef)
		Migration3_4(ffs).migrate(projDef)

		assertFalse(ffs.exists(legacyPath))
		assertEquals(LEGACY_CONTENT, read(settingsPath))
	}

	private companion object {
		const val LEGACY_CONTENT = "[data]\nauthorName = \"Legacy\"\n"
		const val CURRENT_CONTENT = "[data]\nauthorName = \"Current\"\n"
	}
}
