import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.okio.ProjectEditorFactory
import com.darkrockstudios.apps.hammer.common.fileio.okio.ProjectEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.ProjectRepositoryOkio
import io.mockk.*
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectRepositoryTest {
    private lateinit var ffs: FakeFileSystem
    private lateinit var projectsRepo: ProjectsRepository
    private lateinit var mockEditor: ProjectEditorRepositoryOkio
    private lateinit var factory: ProjectEditorFactory
    private val proj1Def = getProject1Def()
    private lateinit var repo: ProjectRepositoryOkio

    @Before
    fun setup() {
        ffs = FakeFileSystem()

        createRootDirectory(ffs)

        val projectsDir = getProjectsDirectory()
        ffs.createDirectory(projectsDir)

        createProjectOne(ffs)

        projectsRepo = mockk()

        mockEditor = mockk()
        justRun { mockEditor.initializeProjectEditor() }
        justRun { mockEditor.close() }

        factory = spyk(
            object : ProjectEditorFactory {
                override fun createEditor(
                    projectDef: ProjectDef,
                    projectsRepository: ProjectsRepository,
                    fileSystem: FileSystem
                ) = mockEditor
            }
        )

        repo = ProjectRepositoryOkio(
            fileSystem = ffs,
            projectsRepository = projectsRepo,
            projectEditorFactory = factory
        )
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
        val editor = repo.getProjectEditor(proj1Def)

        verify { factory.createEditor(proj1Def, projectsRepo, ffs) }
        assertEquals(mockEditor, editor)
    }

    @Test
    fun `Create Already Created Editor`() {
        var editor = repo.getProjectEditor(proj1Def)

        verify { factory.createEditor(proj1Def, projectsRepo, ffs) }
        assertEquals(mockEditor, editor)

        editor = repo.getProjectEditor(proj1Def)
        verify { factory.createEditor(proj1Def, projectsRepo, ffs) wasNot Called }
        assertEquals(mockEditor, editor)
    }

    @Test
    fun `Close Editor`() {
        repo.getProjectEditor(proj1Def)

        assertEquals(1, repo.numActiveEditors())

        repo.closeEditor(proj1Def)
        verify { mockEditor.close() }

        assertEquals(0, repo.numActiveEditors())
    }

    @Test
    fun `Close Editors`() {
        repo.getProjectEditor(proj1Def)

        assertEquals(1, repo.numActiveEditors())

        repo.closeEditors()
        verify { mockEditor.close() }

        assertEquals(0, repo.numActiveEditors())
    }
}