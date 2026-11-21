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
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals

/**
 * File sync tools drop their own files beside the buffers (Syncthing conflict copies, temp files).
 * They must not stop the project from opening.
 */
class SceneDatasourceBufferForeignFileTest : BaseTest() {

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
	fun `Get temp buffer contents skips files that are not buffers`() = runTest {
		val datasource = SceneDatasource(getProjectDef(PROJECT_1_NAME), ffs)
		val scene = datasource.getSceneFromPath(datasource.resolveScenePathFromFilesystem(3)!!)
		datasource.storeTempSceneBuffer(
			SceneBuffer(SceneContent(scene, "buffered text"), source = UpdateSource.Editor)
		)
		val bufferDir = datasource.getSceneBufferDirectory().toOkioPath()
		ffs.write(bufferDir / "3.sync-conflict-20261024-173944-53PQXWR.md") { writeUtf8("other copy") }
		ffs.write(bufferDir / ".syncthing.3.md.tmp") { writeUtf8("partial") }

		val contents = datasource.getSceneTempBufferContents()

		assertEquals(1, contents.size)
		assertEquals(3, contents.first().scene.id)
		assertEquals("buffered text", contents.first().markdown)
	}
}
