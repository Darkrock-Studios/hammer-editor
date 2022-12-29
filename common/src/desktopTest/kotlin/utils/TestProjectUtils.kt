import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.getRootDocumentDirectory
import com.darkrockstudios.apps.hammer.common.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.tree.NodeCoordinates
import com.darkrockstudios.apps.hammer.common.tree.Tree
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import utils.FileResourcesUtils
import kotlin.test.assertEquals

const val PROJECT_1_NAME = "Test Project 1"
const val PROJECT_2_NAME = "Test Project 2"
const val OUT_OF_ORDER_PROJECT_NAME = "Out Of Order"
val projectNames = listOf(OUT_OF_ORDER_PROJECT_NAME, PROJECT_1_NAME, PROJECT_2_NAME)

fun getProject1Def(): ProjectDef {
    val projectPath = getProjectsDirectory().div(PROJECT_1_NAME).toHPath()

    return ProjectDef(
        name = PROJECT_1_NAME,
        path = projectPath
    )
}

fun createRootDirectory(ffs: FakeFileSystem) {
    val rootDir = getRootDocumentDirectory()
    ffs.createDirectories(rootDir.toPath())
}

fun getProjectsDirectory(): Path {
    val rootPath = getRootDocumentDirectory().toPath()
    val proj = GlobalSettingsRepository.DEFAULT_PROJECTS_DIR.toPath()
    val projectsDir = rootPath.div(proj)

    return projectsDir
}

fun createProjectDirectories(ffs: FakeFileSystem) {
    val projDir = getProjectsDirectory()
    ffs.createDirectories(projDir)
    projectNames.forEach { projectName ->
        ffs.createDirectory(projDir.div(projectName), true)
    }
}

/**
 * Create an in-mem project from a predefined resource
 */
fun createProject(ffs: FakeFileSystem, projectName: String) {
    val projDir = getProjectsDirectory()
    ffs.createDirectories(projDir)

    FileResourcesUtils.copyResourceFolderToFakeFileSystem(
        projectName.toPath(),
        getProjectsDirectory(),
        ffs
    )
}

/**
 * This just helps confirm you wrote the test correctly, figuring out the coords
 * by hand can be tricky.
 */
fun verifyCoords(tree: Tree<SceneItem>, coords: NodeCoordinates, id: Int) {
    val child = tree[coords.parentIndex].children()[coords.childLocalIndex]
    assertEquals(id, child.value.id, "Node by coordinates did not match")

    val byGlobal = tree[coords.globalIndex]
    assertEquals(id, byGlobal.value.id, "Global Index did not match")
}