import com.darkrockstudios.apps.hammer.common.fileio.okio.ProjectsRepositoryOkio
import com.darkrockstudios.apps.hammer.common.getRootDocumentDirectory
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectsRepositoryTest {

    private lateinit var ffs: FakeFileSystem

    @Before
    fun setup() {
        ffs = FakeFileSystem()

        val rootDir = getRootDocumentDirectory()
        ffs.createDirectories(rootDir.toPath())
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
}