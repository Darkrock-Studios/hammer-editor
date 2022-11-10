package projecteditorrepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectrepository.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.data.projectrepository.projecteditorrepository.ProjectEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.getRootDocumentDirectory
import com.darkrockstudios.apps.hammer.common.tree.TreeNode
import io.mockk.every
import io.mockk.mockk
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import utils.callPrivate
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectEditorRepositoryOkioTestSimple {

    private lateinit var ffs: FakeFileSystem
    private lateinit var projectPath: HPath
    private lateinit var scenesPath: HPath
    private lateinit var projectsRepo: ProjectsRepository
    private lateinit var projectDef: ProjectDef

    private val sceneFiles = mapOf(
        "1-Scene 1-1.md" to "This is scene 1 content",
        "2-Scene 2-2.md" to "This is scene 2 content",
        "3-Scene 3-3.md" to "This is scene 3 content"
    )

    private fun populateProject(fs: FakeFileSystem) {
        fs.createDirectories(projectPath.toOkioPath())
        scenesPath = projectPath.toOkioPath().div(ProjectEditorRepository.SCENE_DIRECTORY).toHPath()

        fs.createDirectory(scenesPath.toOkioPath())

        sceneFiles.entries.forEach { (name, content) ->
            val path = scenesPath.toOkioPath().div(name)
            fs.write(path) {
                writeUtf8(content)
            }
        }
        assertEquals(sceneFiles.size, fs.list(scenesPath.toOkioPath()).size)
    }

    private fun scenePath(filename: String): HPath {
        return scenesPath.toOkioPath().div(filename).toHPath()
    }

    @Before
    fun setup() {
        ffs = FakeFileSystem()

        val rootDir = getRootDocumentDirectory()
        ffs.createDirectories(rootDir.toPath())

        projectsRepo = mockk()
        every { projectsRepo.getProjectsDirectory() } returns
                rootDir.toPath().div(PROJ_DIR).toHPath()

        projectPath = projectsRepo.getProjectsDirectory().toOkioPath().div(PROJ_NAME).toHPath()

        populateProject(ffs)

        projectDef = ProjectDef(
            name = PROJ_NAME,
            path = projectPath
        )
    }

    @After
    fun tearDown() {
        ffs.checkNoOpenFiles()
    }

    @Test
    fun `Get filename`() {
        val repo = ProjectEditorRepositoryOkio(
            projectDef = projectDef,
            projectsRepository = projectsRepo,
            fileSystem = ffs
        )

        val expectedFilename = sceneFiles.entries.first().key
        val scenePath = scenePath(expectedFilename)
        val sceneFilename = repo.getSceneFilename(scenePath)
        assertEquals(expectedFilename, sceneFilename)
    }

    @Test
    fun `Load Scene Tree`() {
        val repo = ProjectEditorRepositoryOkio(
            projectDef = projectDef,
            projectsRepository = projectsRepo,
            fileSystem = ffs
        )

        val sceneTree: TreeNode<SceneItem> = repo.callPrivate("loadSceneTree")

        assertEquals(3, sceneTree.numChildrenRecursive())
        assertEquals(1, sceneTree[0].value.id)
        assertEquals(2, sceneTree[1].value.id)
        assertEquals(3, sceneTree[2].value.id)
    }

    @Test
    fun `Init Editor`() {
        val repo = ProjectEditorRepositoryOkio(
            projectDef = projectDef,
            projectsRepository = projectsRepo,
            fileSystem = ffs
        )

        repo.initializeProjectEditor()

        repo.close()
    }

    companion object {
        const val PROJ_DIR = "HammerProjects"
        const val PROJ_NAME = "Test Proj"
    }
}