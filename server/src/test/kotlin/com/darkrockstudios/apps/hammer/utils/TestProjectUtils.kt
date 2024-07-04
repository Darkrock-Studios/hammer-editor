import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectFilesystemDatasource
import com.darkrockstudios.apps.hammer.projects.ProjectsFileSystemDatasource
import com.darkrockstudios.apps.hammer.utils.FileResourcesUtils
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

const val PROJECT_1_NAME = "Test Project 1"
val projectNames = listOf(
	PROJECT_1_NAME,
)

fun getRootDirectory(ffs: FakeFileSystem): Path {
	val rootDir = ProjectsFileSystemDatasource.getRootDirectory(ffs)
	return rootDir
}

fun createRootDirectory(ffs: FakeFileSystem) {
	val rootDir = getRootDirectory(ffs)
	ffs.createDirectories(rootDir)
}

fun getUserDirectory(userId: Long, ffs: FakeFileSystem): Path {
	return ProjectsFileSystemDatasource.getUserDirectory(userId, ffs)
}

fun getProjectDirectory(userId: Long, projectName: String, ffs: FakeFileSystem): Path {
	return ProjectFilesystemDatasource.getProjectDirectory(
		userId,
		ProjectDefinition(projectName),
		ffs
	)
}

/**
 * Create an in-mem project from a predefined resource
 */
fun createProject(userId: Long, projectName: String, ffs: FakeFileSystem) {
	val projDir = getProjectDirectory(userId, projectName, ffs)
	ffs.createDirectories(projDir)

	FileResourcesUtils.copyResourceFolderToFakeFileSystem(
		projectName.toPath(),
		getUserDirectory(userId, ffs),
		ffs
	)
}
