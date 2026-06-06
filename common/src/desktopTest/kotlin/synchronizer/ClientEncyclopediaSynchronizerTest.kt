package synchronizer

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceRemapper
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientEncyclopediaSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.StringResource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest

class ClientEncyclopediaSynchronizerTest : BaseTest() {

	private val projectDef = ProjectDef(
		name = "Test",
		path = HPath("/projects/Test", "Test", false),
	)

	@MockK
	private lateinit var serverProjectApi: ServerProjectApi

	@MockK
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	@MockK(relaxed = true)
	private lateinit var encyclopediaRepository: EncyclopediaRepository

	@MockK(relaxed = true)
	private lateinit var referenceRemapper: ReferenceRemapper

	private val strRes: StrRes = object : StrRes {
		override suspend fun get(str: StringResource) = "test"
		override suspend fun get(str: StringResource, vararg args: Any) = "test"
	}

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this)

		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }
				scoped { encyclopediaRepository }
				scoped<ReferenceRemapper> { referenceRemapper }
			}
		})
	}

	private fun newSynchronizer() = ClientEncyclopediaSynchronizer(
		projectDef = projectDef,
		serverProjectApi = serverProjectApi,
		projectMetadataDatasource = projectMetadataDatasource,
		strRes = strRes,
	)

	@Test
	fun `reIdEntity remaps references after re-IDing the entry`() = runTest {
		// Defends the cross-entity wiring we added for sync ID-conflict resolution:
		// when an encyclopedia entry's ID is rewritten on the client, every scene's
		// confirmedReferences and dismissedReferences must also be rewritten or the
		// references silently break. The order matters - the entry must be re-IDed
		// first so the remapper sees the new ID consistent with the entry store.
		coEvery { encyclopediaRepository.reIdEntry(5, 12) } returns Unit
		coEvery { referenceRemapper.remapEntryReferences(5, 12) } returns Unit

		val sync = newSynchronizer()
		sync.reIdEntity(oldId = 5, newId = 12)

		coVerifyOrder {
			encyclopediaRepository.reIdEntry(5, 12)
			referenceRemapper.remapEntryReferences(5, 12)
		}
		coVerify(exactly = 1) { referenceRemapper.remapEntryReferences(5, 12) }
	}
}
