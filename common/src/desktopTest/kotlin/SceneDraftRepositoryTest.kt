import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projectrepository.ProjectRepository
import io.mockk.mockk
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test

class SceneDraftRepositoryTest {

    @Test
    fun `Get Draft Path`() {
        val projectRepository: ProjectRepository = mockk()
        val fs = FakeFileSystem()
        val repo = SceneDraftRepositoryOkio(
            projectRepository = projectRepository,
            fileSystem = fs
        )

        // TODO implement the test, add drafts
        /*
        val projDef = ProjectDef("Test", HPath())
        SceneItem()
        repo.getDraftPath()
        */
    }
}