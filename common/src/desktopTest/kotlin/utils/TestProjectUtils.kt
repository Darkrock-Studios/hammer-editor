import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.getRootDocumentDirectory
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import utils.FileResourcesUtils

const val PROJECT_1_NAME = "Test Project 1"
val projectNames = listOf(PROJECT_1_NAME, "Project 2", "Project 3")

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
    val proj = ProjectsRepository.PROJECTS_DIR.toPath()
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

fun createProjectOne(ffs: FakeFileSystem) {
    val projDir = getProjectsDirectory()
    ffs.createDirectories(projDir)

    FileResourcesUtils.copyResourceFolderToFakeFileSystem(
        PROJECT_1_NAME.toPath(),
        getProjectsDirectory(),
        ffs
    )
}