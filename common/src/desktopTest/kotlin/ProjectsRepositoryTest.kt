import com.darkrockstudios.apps.hammer.common.data.ProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepositoryOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectsRepositoryTest {

    private lateinit var ffs: FakeFileSystem

    @Before
    fun setup() {
        ffs = FakeFileSystem()

        createRootDirectory(ffs)
    }

    @After
    fun teardown() {
        ffs.checkNoOpenFiles()
    }

    @Test
    fun `ProjectsRepository init`() {
        val projDir = getProjectsDirectory()
        assertFalse(ffs.exists(projDir), "Dir should not have existed already")

        val repo = ProjectsRepositoryOkio(ffs)

        assertTrue(ffs.exists(projDir), "Init did not create project dir")
    }

    @Test
    fun `Scene Name Validation`() {
        val repo = ProjectsRepositoryOkio(ffs)

        assertTrue(repo.validateFileName("good"))
        assertFalse(repo.validateFileName(null))
        assertFalse(repo.validateFileName(""))
        assertFalse(repo.validateFileName("bad*bad"))
        assertFalse(repo.validateFileName("bad-bad"))
        assertFalse(repo.validateFileName("bad/bad"))
        assertFalse(repo.validateFileName("""bad\bad"""))
    }

    @Test
    fun `Get Projects Directory`() {
        val actualProjDir = getProjectsDirectory().toHPath()
        val repo = ProjectsRepositoryOkio(ffs)
        val projectDir = repo.getProjectsDirectory()
        assertEquals(actualProjDir, projectDir)
    }

    @Test
    fun `Get Projects`() {
        createProjectDirectories(ffs)

        val repo = ProjectsRepositoryOkio(ffs)
        val projects = repo.getProjects()

        assertEquals(projectNames.size, projects.size)
        projects.forEachIndexed { index, project ->
            assertEquals(projectNames[index], project.name)
        }
    }

    @Test
    fun `Get Project Directory`() {
        createProjectDirectories(ffs)
        val repo = ProjectsRepositoryOkio(ffs)

        val projectName = projectNames[0]
        val projectDir = repo.getProjectDirectory(projectName)

        val actualProjDir = getProjectsDirectory().div(projectName)
        assertEquals(actualProjDir, projectDir.toOkioPath())
    }

    @Test
    fun `Create Project`() {
        val repo = ProjectsRepositoryOkio(ffs)

        val projectName = projectNames[0]
        val created = repo.createProject(projectName)
        assertTrue(created)

        val actualProjDir = getProjectsDirectory().div(projectName)
        assertTrue(ffs.exists(actualProjDir))

        val created2 = repo.createProject(projectName)
        assertFalse(created2)
    }

    @Test
    fun `Delete Project`() {
        createProjectDirectories(ffs)
        val repo = ProjectsRepositoryOkio(ffs)

        val projectName = projectNames[0]
        val projPath = getProjectsDirectory().div(projectName)
        val projDef = ProjectDefinition(projectName, projPath.toHPath())

        val deleted = repo.deleteProject(projDef)
        assertTrue(deleted)

        assertFalse(ffs.exists(projPath))

        val deleteAgain = repo.deleteProject(projDef)
        assertFalse(deleteAgain)
    }
}