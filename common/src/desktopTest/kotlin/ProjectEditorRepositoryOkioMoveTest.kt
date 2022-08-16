import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.ProjectEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.getRootDocumentDirectory
import com.darkrockstudios.apps.hammer.common.tree.NodeCoordinates
import com.darkrockstudios.apps.hammer.common.tree.Tree
import com.darkrockstudios.apps.hammer.common.tree.TreeNode
import io.mockk.every
import io.mockk.mockk
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test
import utils.getPrivateProperty
import kotlin.test.assertEquals

class ProjectEditorRepositoryOkioMoveTest {

    private lateinit var ffs: FakeFileSystem
    private lateinit var projectPath: HPath
    private lateinit var projectsRepo: ProjectsRepository
    private lateinit var projectDef: ProjectDef
    private lateinit var repo: ProjectEditorRepository

    private fun verify(node: TreeNode<SceneItem>, print: Boolean = false, vararg ids: Int) {
        assertEquals(ids.size, node.children().size)

        if (print) {
            node.children().forEachIndexed { index, childNode ->
                println("$index - ${childNode.value.id}")
            }
        }

        node.children().forEachIndexed { index, child ->
            assertEquals(index, child.value.order, "Out of order")
            assertEquals(ids[index], child.value.id, "IDs are out of order")
        }
    }

    private fun verifyCoords(tree: Tree<SceneItem>, coords: NodeCoordinates, id: Int) {
        val child = tree[coords.parentIndex].children()[coords.childLocalIndex]
        assertEquals(id, child.value.id, "Node by coordinates did not match")

        val byGlobal = tree[coords.globalIndex]
        assertEquals(id, byGlobal.value.id, "Global Index did not match")
    }

    @Before
    fun setup() {
        ffs = FakeFileSystem()

        val rootDir = getRootDocumentDirectory()
        ffs.createDirectories(rootDir.toPath())

        projectsRepo = mockk()
        every { projectsRepo.getProjectsDirectory() } returns
                rootDir.toPath().div(PROJ_DIR).toHPath()

        projectPath = projectsRepo.getProjectsDirectory().toOkioPath().div(PROJECT_1_NAME).toHPath()

        projectDef = ProjectDef(
            name = PROJECT_1_NAME,
            path = projectPath
        )

        createProjectOne(ffs)

        repo = ProjectEditorRepositoryOkio(
            projectDef = projectDef,
            projectsRepository = projectsRepo,
            fileSystem = ffs
        )

        repo.initializeProjectEditor()
    }

    @After
    fun tearDown() {
        repo.close()

        ffs.checkNoOpenFiles()
    }

    @Test
    fun `Verify Initial Layout`() {
        val tree = repo.getPrivateProperty<ProjectEditorRepository, Tree<SceneItem>>("sceneTree")

        for (index in 0..tree.numChildrenRecursive()) {
            assertEquals(index, tree[index].value.id)
        }
    }

    @Test
    fun `Move Scene Sibling, Higher to Lower`() {
        val tree = repo.getPrivateProperty<ProjectEditorRepository, Tree<SceneItem>>("sceneTree")
        val coords = NodeCoordinates(
            parentIndex = 0,
            childLocalIndex = 2,
            globalIndex = 6
        )
        verifyCoords(tree, coords, 6)

        val moveRequest = MoveRequest(
            id = 1,
            position = InsertPosition(
                coords = coords,
                before = false
            )
        )
        repo.moveScene(moveRequest)

        val afterTree =
            repo.getPrivateProperty<ProjectEditorRepository, Tree<SceneItem>>("sceneTree")
        // Initial Order: 1 2 6 7
        verify(afterTree.root(), false, 2, 6, 1, 7)
    }

    @Test
    fun `Move Scene Lower to Higher`() {
        val tree = repo.getPrivateProperty<ProjectEditorRepository, Tree<SceneItem>>("sceneTree")
        val coords = NodeCoordinates(
            parentIndex = 0,
            childLocalIndex = 0,
            globalIndex = 1
        )
        verifyCoords(tree, coords, 1)

        val moveRequest = MoveRequest(
            id = 6,
            position = InsertPosition(
                coords = coords,
                before = false
            )
        )
        repo.moveScene(moveRequest)

        // Initial Order: 1 2 6 7
        val afterTree =
            repo.getPrivateProperty<ProjectEditorRepository, Tree<SceneItem>>("sceneTree")
        verify(afterTree.root(), false, 1, 6, 2, 7)
    }

    companion object {
        const val PROJ_DIR = "HammerProjects"
    }
}