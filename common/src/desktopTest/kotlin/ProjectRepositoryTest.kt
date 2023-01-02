import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.id.provider.IdProvider
import com.darkrockstudios.apps.hammer.common.data.projectrepository.ProjectEditorFactory
import com.darkrockstudios.apps.hammer.common.data.projectrepository.ProjectRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projectrepository.projecteditorrepository.ProjectEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
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
    private lateinit var idRepository: IdRepository
    private lateinit var isProvider: IdProvider
    private var nextId = -1
    private lateinit var factory: ProjectEditorFactory
    private val proj1Def = getProject1Def()
    private lateinit var repo: ProjectRepositoryOkio
    private lateinit var toml: Toml

    private fun claimId(): Int {
        val id = nextId
        nextId++
        return id
    }

    @Before
    fun setup() {
        ffs = FakeFileSystem()

        createRootDirectory(ffs)

        val projectsDir = getProjectsDirectory()
        ffs.createDirectory(projectsDir)

        createProject(ffs, PROJECT_1_NAME)

        projectsRepo = mockk()

        nextId = -1
        isProvider = mockk()
        every { isProvider.claimNextSceneId() } answers { claimId() }

        idRepository = mockk()
        every { idRepository.getIdProvider(any()) } returns isProvider

        mockEditor = mockk()
        justRun { mockEditor.initializeProjectEditor() }
        justRun { mockEditor.close() }

        toml = createTomlSerializer()

        factory = spyk(
            object : ProjectEditorFactory {
                override fun createEditor(
                    projectDef: ProjectDef,
                    projectsRepository: ProjectsRepository,
                    idRepository: IdRepository,
                    fileSystem: FileSystem,
                    toml: Toml
                ) = mockEditor
            }
        )

        repo = ProjectRepositoryOkio(
            fileSystem = ffs,
            projectsRepository = projectsRepo,
            idRepository = idRepository,
            toml = toml,
            projectEditorFactory = factory
        )
    }

    @After
    fun teardown() {
        ffs.checkNoOpenFiles()
    }

    @Test
    fun `Test Proj 1 Setup`() {
        createProject(ffs, PROJECT_1_NAME)

        val projPath = getProjectsDirectory().div(PROJECT_1_NAME)
        assertTrue(ffs.exists(projPath))
    }

    @Test
    fun `Create Project Editor`() {
        val editor = repo.getProjectEditor(proj1Def)

        verify { factory.createEditor(proj1Def, projectsRepo, idRepository, ffs, toml) }
        assertEquals(mockEditor, editor)
    }

    @Test
    fun `Create Already Created Editor`() {
        var editor = repo.getProjectEditor(proj1Def)

        verify { factory.createEditor(proj1Def, projectsRepo, idRepository, ffs, toml) }
        assertEquals(mockEditor, editor)

        editor = repo.getProjectEditor(proj1Def)
        verify { factory.createEditor(proj1Def, projectsRepo, idRepository, ffs, toml) wasNot Called }
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