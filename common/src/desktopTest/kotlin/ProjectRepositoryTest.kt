import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.okio.ProjectEditorFactory
import com.darkrockstudios.apps.hammer.common.fileio.okio.ProjectRepositoryOkio
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectRepositoryTest {
    private lateinit var ffs: FakeFileSystem

    @Before
    fun setup() {
        ffs = FakeFileSystem()

        createRootDirectory(ffs)

        val projectsDir = getProjectsDirectory()
        ffs.createDirectory(projectsDir)
    }

    @After
    fun teardown() {
        ffs.checkNoOpenFiles()
    }

    @Test
    fun `Test Proj 1 Setup`() {
        createProjectOne(ffs)

        val projPath = getProjectsDirectory().div(PROJECT_1_NAME)
        assertTrue(ffs.exists(projPath))
    }

    @Test
    fun `Create Project Editor`() {
        createProjectOne(ffs)

        val projectsRepo: ProjectsRepository = mockk()
        val proj1Def = getProject1Def()

        val mockEditor: ProjectEditorRepository = mockk()
        every { mockEditor.initializeProjectEditor() } returns Unit

        val factory = spyk(
            object : ProjectEditorFactory {
                override fun createEditor(
                    projectDef: ProjectDef,
                    projectsRepository: ProjectsRepository,
                    fileSystem: FileSystem
                ) = mockEditor
            }
        )

        val repo = ProjectRepositoryOkio(
            fileSystem = ffs,
            projectsRepository = projectsRepo,
            projectEditorFactory = factory
        )

        val editor = repo.getProjectEditor(proj1Def)

        verify { factory.createEditor(proj1Def, projectsRepo, ffs) }
        assertEquals(mockEditor, editor)
    }
}