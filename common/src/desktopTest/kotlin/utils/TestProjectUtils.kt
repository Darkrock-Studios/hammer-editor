import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.groupmetadata.GroupMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.tree.NodeCoordinates
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import utils.FileResourcesUtils
import java.util.UUID
import kotlin.test.assertEquals

const val PROJECT_EMPTY_NAME = "Empty Project"
const val PROJECT_1_NAME = "Test Project 1"
const val PROJECT_2_NAME = "Test Project 2"
const val OUT_OF_ORDER_PROJECT_NAME = "Out Of Order"
const val ENCYCLOPEDIA_ONLY_PROJECT_NAME = "Encyclopedia Only"
const val MIGRATION_0_1 = "Migration 0 to 1"
const val MIGRATION_0_1_ALREADY = "Migration 0 to 1 Already"
const val MIGRATION_0_1_NO_TIMELINE = "Migration 0 to 1 No Timeline"
val projectNames = listOf(
	PROJECT_EMPTY_NAME,
	OUT_OF_ORDER_PROJECT_NAME,
	PROJECT_1_NAME,
	PROJECT_2_NAME,
	ENCYCLOPEDIA_ONLY_PROJECT_NAME
)

// The ids in the resource fixtures' project.toml files. Test Project 1 holds a legacy serverProjectId.
private val fixtureProjectIds = mapOf(
	PROJECT_EMPTY_NAME to "f70c0c04-a216-49fb-af1a-6a3a11b1328f",
	OUT_OF_ORDER_PROJECT_NAME to "2a524307-6a15-4200-b811-78483cf81b7c",
	PROJECT_1_NAME to "5f1d7446-1f08-4909-a81e-cdc7470a2f63",
	PROJECT_2_NAME to "8c3f6a2e-4b1d-4e5a-9f7c-2d6b1a0e9c43",
	ENCYCLOPEDIA_ONLY_PROJECT_NAME to "b83682a2-1973-434e-9f9b-a9cdb961a437",
	MIGRATION_0_1 to "8cafbad6-1d00-4138-86d5-672a3e2f5b3f",
	MIGRATION_0_1_ALREADY to "07d38a29-2370-4b82-9962-0bb09f40500d",
	MIGRATION_0_1_NO_TIMELINE to "c3f2f6e0-d1b7-40f8-89ae-f2d3e78ab9cb",
)

/**
 * A stable id per project name: a fixture's own id, else one derived from the name. UUID-shaped,
 * since account sync drops ids that are not.
 */
fun testProjectId(name: String): ProjectId =
	ProjectId(fixtureProjectIds[name] ?: UUID.nameUUIDFromBytes(name.encodeToByteArray()).toString())

/** A stable, UUID-shaped id per group name. */
fun testGroupId(name: String): GroupId =
	GroupId(UUID.nameUUIDFromBytes("group:$name".encodeToByteArray()).toString())

/** A marked group directory named [name] in [parent], the projects directory by default. */
fun createGroupDirectory(
	ffs: FakeFileSystem,
	name: String,
	parent: Path = getProjectsDirectory(),
	groupId: GroupId = testGroupId(name),
): Path {
	val dir = parent / ProjectsRepository.encodeForFilename(name)
	ffs.createDirectories(dir)
	GroupMetadataDatasource(ffs, createTomlSerializer()).createMetadata(dir.toHPath(), groupId)
	return dir
}

/** A marked project directory named [name] in [parent], under [testProjectId]. */
fun createNestedProjectDirectory(ffs: FakeFileSystem, name: String, parent: Path): ProjectDef {
	val dir = parent / ProjectsRepository.encodeForFilename(name)
	ffs.createDirectories(dir)
	val def = ProjectDef(testProjectId(name), name, dir.toHPath())
	ProjectMetadataDatasource(ffs, createTomlSerializer()).createMetadata(def, def.id)
	return def
}

/** The id a fresh listing reads for the project named like [projectDef]. */
fun ProjectsRepository.listedProjectId(projectDef: ProjectDef): ProjectId =
	checkNotNull(findProject(projectDef.name)) { "No project named ${projectDef.name}" }.id

fun getProjectDef(name: String): ProjectDef {
	val projectPath = getProjectsDirectory().div(name).toHPath()

	return ProjectDef(
		id = testProjectId(name),
		name = name,
		path = projectPath
	)
}

fun getProject1Def(): ProjectDef {
	val projectPath = getProjectsDirectory().div(PROJECT_1_NAME).toHPath()

	return ProjectDef(
		id = testProjectId(PROJECT_1_NAME),
		name = PROJECT_1_NAME,
		path = projectPath
	)
}

fun createRootDirectory(ffs: FakeFileSystem) {
	val rootDir = getDefaultRootDocumentDirectory()
	ffs.createDirectories(rootDir.toPath())
}

fun getProjectsDirectory(): Path {
	val rootPath = getDefaultRootDocumentDirectory().toPath()
	val proj = GlobalSettingsStore.DEFAULT_PROJECTS_DIR.toPath()
	val projectsDir = rootPath.div(proj)

	return projectsDir
}

fun createProjectDirectories(ffs: FakeFileSystem) {
	val projDir = getProjectsDirectory()
	ffs.createDirectories(projDir)
	val metadataDatasource = ProjectMetadataDatasource(ffs, createTomlSerializer())
	projectNames.forEach { projectName ->
		val dir = projDir.div(projectName)
		ffs.createDirectory(dir, true)
		metadataDatasource.createMetadata(getProjectDef(projectName), testProjectId(projectName))
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