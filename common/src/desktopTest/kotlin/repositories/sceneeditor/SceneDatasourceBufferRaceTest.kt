package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import kotlinx.coroutines.test.runTest
import okio.ForwardingFileSystem
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals

/**
 * Reproduces the listing-then-stat race for scene temp buffers: a file is deleted between
 * `list()` and the per-entry `metadata()` call. getSceneTempBufferContents must skip the
 * vanished entry rather than throwing FileNotFoundException.
 */
class SceneDatasourceBufferRaceTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		createProject(ffs, PROJECT_1_NAME)
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		ffs.checkNoOpenFiles()
	}

	@Test
	fun `Get temp buffer contents skips entries deleted during listing`() = runTest {
		val projectDef = getProjectDef(PROJECT_1_NAME)
		val datasource = SceneDatasource(projectDef, ffs)

		val bufferDir = datasource.getSceneBufferDirectory().toOkioPath()

		val scenePath = datasource.resolveScenePathFromFilesystem(3)!!
		val scene = datasource.getSceneFromPath(scenePath)
		datasource.storeTempSceneBuffer(
			SceneBuffer(SceneContent(scene, "buffered text"), source = UpdateSource.Editor)
		)

		val phantom = bufferDir.div("99999.md")
		val racingFs = object : ForwardingFileSystem(ffs) {
			override fun list(dir: Path): List<Path> =
				if (dir == bufferDir) super.list(dir) + phantom else super.list(dir)
		}

		val racingDatasource = SceneDatasource(projectDef, racingFs)
		val contents = racingDatasource.getSceneTempBufferContents()

		assertEquals(1, contents.size)
		assertEquals(3, contents.first().scene.id)
		assertEquals("buffered text", contents.first().markdown)
	}
}
