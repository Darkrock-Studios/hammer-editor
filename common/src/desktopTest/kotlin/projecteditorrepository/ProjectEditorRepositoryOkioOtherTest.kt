package projecteditorrepository

import OUT_OF_ORDER_PROJECT_NAME
import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.ProjectEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.getRootDocumentDirectory
import com.darkrockstudios.apps.hammer.common.tree.Tree
import com.darkrockstudios.apps.hammer.common.tree.TreeNode
import createProject
import io.mockk.every
import io.mockk.mockk
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test
import utils.callPrivate
import utils.getPrivateProperty
import kotlin.test.*

class ProjectEditorRepositoryOkioOtherTest {

    private lateinit var ffs: FakeFileSystem
    private lateinit var projectPath: HPath
    private lateinit var projectsRepo: ProjectsRepository
    private lateinit var projectDef: ProjectDef
    private lateinit var repo: ProjectEditorRepository

    private fun verifyTreeAndFilesystem() {
        val tree = repo.getPrivateProperty<ProjectEditorRepository, Tree<SceneItem>>("sceneTree")

        // Verify tree nodes match file system nodes
        tree.filter { !it.value.isRootScene }.forEach { node ->
            val path = repo.getSceneFilePath(node.value.id)
            val fsScene = repo.getSceneFromPath(path)
            assertEquals(node.value, fsScene)
        }
    }

    @Before
    fun setup() {
        ffs = FakeFileSystem()

        val rootDir = getRootDocumentDirectory()
        ffs.createDirectories(rootDir.toPath())

        projectsRepo = mockk()
        every { projectsRepo.getProjectsDirectory() } returns
                rootDir.toPath().div(ProjectEditorRepositoryOkioMoveTest.PROJ_DIR).toHPath()
    }

    @After
    fun tearDown() {
        repo.close()

        ffs.checkNoOpenFiles()
    }

    private fun configure(projectName: String) {
        projectPath = projectsRepo.getProjectsDirectory().toOkioPath().div(projectName).toHPath()

        projectDef = ProjectDef(
            name = projectName,
            path = projectPath
        )

        createProject(ffs, projectName)

        repo = ProjectEditorRepositoryOkio(
            projectDef = projectDef,
            projectsRepository = projectsRepo,
            fileSystem = ffs
        )
    }

    /**
     * Load a project who's scene's have irregular order numbers
     * On `initializeProjectEditor()` the orders will be cleaned up
     */
    @Test
    fun `Cleanup Scene Order`() {
        configure(OUT_OF_ORDER_PROJECT_NAME)

        val beforeSceneTree: TreeNode<SceneItem> = repo.callPrivate("loadSceneTree")

        repo.initializeProjectEditor()

        val afterSceneTree: TreeNode<SceneItem> = repo.callPrivate("loadSceneTree")

        // Make sure the tree was actually changed, initializeProjectEditor()
        // should clean up this out of order project
        assertNotEquals(beforeSceneTree, afterSceneTree)

        verifyTreeAndFilesystem()

        val tree = repo.getPrivateProperty<ProjectEditorRepository, Tree<SceneItem>>("sceneTree")
        tree.forEachIndexed { index, node ->
            when (index) {
                0 -> assertTrue(node.value.isRootScene)
                1 -> {
                    assertEquals(2, node.value.id)
                    assertEquals(0, node.value.order)
                }
                2 -> {
                    assertEquals(3, node.value.id)
                    assertEquals(0, node.value.order)
                }
                3 -> {
                    assertEquals(4, node.value.id)
                    assertEquals(1, node.value.order)
                }
                4 -> {
                    assertEquals(5, node.value.id)
                    assertEquals(2, node.value.order)
                }
                5 -> {
                    assertEquals(1, node.value.id)
                    assertEquals(1, node.value.order)
                }
                6 -> {
                    assertEquals(6, node.value.id)
                    assertEquals(2, node.value.order)
                }
                7 -> {
                    assertEquals(7, node.value.id)
                    assertEquals(3, node.value.order)
                }
            }
        }
    }

    @Test
    fun `Create Scene, Invalid Scene Name`() {
        configure(PROJECT_1_NAME)

        every { projectsRepo.validateFileName(any()) } returns false

        repo.initializeProjectEditor()

        val sceneName = "New Scene"

        val newScene = repo.createScene(null, sceneName)
        assertNull(newScene, "Scene should not have been created")
    }

    @Test
    fun `Create Scene, Under Root`() {
        configure(PROJECT_1_NAME)

        every { projectsRepo.validateFileName(any()) } returns true

        repo.initializeProjectEditor()

        verifyTreeAndFilesystem()

        val sceneName = "New Scene"

        val newScene = repo.createScene(null, sceneName)
        assertNotNull(newScene)
        assertEquals(sceneName, newScene.name, "Name was incorrect")
        assertEquals(newScene.type, SceneItem.Type.Scene, "Should be scene")
        assertEquals(8, newScene.id, "ID was incorrect")
        assertEquals(4, newScene.order, "Order was incorrect")

        verifyTreeAndFilesystem()
    }

    @Test
    fun `Create Scene, In Group`() {
        configure(PROJECT_1_NAME)

        every { projectsRepo.validateFileName(any()) } returns true

        repo.initializeProjectEditor()

        verifyTreeAndFilesystem()

        val sceneName = "New Scene"
        val group = repo.getSceneItemFromId(2)
        assertNotNull(group)
        assertEquals("Chapter ID 2", group.name)

        val newScene = repo.createScene(group, sceneName)

        repo.getPrivateProperty<ProjectEditorRepository, Tree<SceneItem>>("sceneTree").print()

        assertNotNull(newScene)
        assertEquals(sceneName, newScene.name, "Name was incorrect")
        assertEquals(newScene.type, SceneItem.Type.Scene, "Should be scene")
        assertEquals(8, newScene.id, "ID was incorrect")
        assertEquals(3, newScene.order, "Order was incorrect")

        verifyTreeAndFilesystem()
    }
}