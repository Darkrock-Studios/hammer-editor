import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.common.data.ProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepositoryOkio
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.globalsettings.GlobalSettingsRepository
import io.mockk.every
import io.mockk.mockk
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectsRepositoryTest {

    private lateinit var ffs: FakeFileSystem
    private lateinit var settingsRepo: GlobalSettingsRepository
    private lateinit var settings: GlobalSettings
    private lateinit var toml: Toml

    @Before
    fun setup() {
        toml = createTomlSerializer()

        settingsRepo = mockk()
        settings = mockk()
        every { settingsRepo.globalSettings } answers { settings }
        every { settings.projectsDirectory } answers { getProjectsDirectory().toString() }

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

        val repo = ProjectsRepositoryOkio(ffs, toml, settingsRepo)

        assertTrue(ffs.exists(projDir), "Init did not create project dir")
    }

    @Test
    fun `Scene Name Validation`() {
        val repo = ProjectsRepositoryOkio(ffs, toml, settingsRepo)

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
        val repo = ProjectsRepositoryOkio(ffs, toml, settingsRepo)
        val projectDir = repo.getProjectsDirectory()
        assertEquals(actualProjDir, projectDir)
    }

    @Test
    fun `Get Projects`() {
        createProjectDirectories(ffs)

        val repo = ProjectsRepositoryOkio(ffs, toml, settingsRepo)
        val projects = repo.getProjects()

        assertEquals(projectNames.size, projects.size)
        projects.forEachIndexed { index, project ->
            assertEquals(projectNames[index], project.name)
        }
    }

    @Test
    fun `Get Project Directory`() {
        createProjectDirectories(ffs)
        val repo = ProjectsRepositoryOkio(ffs, toml, settingsRepo)

        val projectName = projectNames[0]
        val projectDir = repo.getProjectDirectory(projectName)

        val actualProjDir = getProjectsDirectory().div(projectName)
        assertEquals(actualProjDir, projectDir.toOkioPath())
    }

    @Test
    fun `Create Project`() {
        val repo = ProjectsRepositoryOkio(ffs, toml, settingsRepo)

        val projectName = projectNames[0]
        val created = repo.createProject(projectName)
        assertTrue(created)

        val actualProjDir = getProjectsDirectory().div(projectName)
        assertTrue(ffs.exists(actualProjDir))

        val created2 = repo.createProject(projectName)
        assertFalse(created2)

        assertFalse { true }
    }

    @Test
    fun `Delete Project`() {
        createProjectDirectories(ffs)
        val repo = ProjectsRepositoryOkio(ffs, toml, settingsRepo)

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