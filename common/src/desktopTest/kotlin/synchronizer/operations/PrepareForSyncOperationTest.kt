package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdService
import com.darkrockstudios.apps.hammer.common.data.duplicateids.ProjectFileRationalizer
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.DuplicateIdFilesException
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.PrepareForSyncOperation
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import okio.fakefilesystem.FakeFileSystem
import utils.TestStrRes
import getProjectDef
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import synchronizer.MockSynchronizers
import synchronizer.addSynchronizers
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PrepareForSyncOperationTest : BaseTest() {

	private lateinit var mockSynchronizers: MockSynchronizers

	@MockK
	private lateinit var idAllocator: IdAllocator

	@MockK(relaxed = true)
	private lateinit var syncJournal: SyncJournal

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this)

		mockSynchronizers = MockSynchronizers(false)
	}

	private fun configureKoin(projectDef: ProjectDef) {
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }

				addSynchronizers(mockSynchronizers)
			}
		})
	}

	private val projectFiles = FakeFileSystem()

	private fun createOperation(projectDef: ProjectDef): PrepareForSyncOperation {
		configureKoin(projectDef)
		return PrepareForSyncOperation(
			projectDef = projectDef,
			entitySynchronizers = EntitySynchronizers(projectDef),
			idAllocator = idAllocator,
			syncJournal = syncJournal,
			duplicateIdService = DuplicateIdService(
				projectDef = projectDef,
				rationalizer = ProjectFileRationalizer(projectDef, projectFiles, createTomlSerializer()),
				syncJournal = syncJournal,
				backupService = mockk(relaxed = true),
			),
			strRes = TestStrRes(),
		)
	}

	@Test
	fun `Files sharing an id stop the sync before anything is read`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val op = createOperation(projectDef)
		val dir = projectDef.path.toOkioPath() / "notes"
		projectFiles.createDirectories(dir / "old")
		val note = "[note]\nid = 5\ncreated = \"2023-11-14T22:13:20Z\"\ncontent = \"x\"\n"
		projectFiles.write(dir / "note-5.toml") { writeUtf8(note) }
		projectFiles.write(dir / "old" / "note-5.toml") { writeUtf8(note.replace("x", "y")) }

		val result = op.execute(
			state = InitialSyncOperationState(false),
			onProgress = mockk(relaxed = true),
			onLog = mockk(relaxed = true),
			onConflict = mockk(relaxed = true),
			onComplete = mockk(relaxed = true),
		)

		assertIs<DuplicateIdFilesException>(assertIs<ClientResult.Failure<*>>(result).exception)
		coVerify(exactly = 0) { idAllocator.findNextId() }
	}

	@Test
	fun `Golden Path`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery { idAllocator.findNextId() } just Runs
		mockSynchronizers.synchronizers.forEach { synchronizer ->
			coEvery { synchronizer.prepareForSync() } just Runs
		}

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend (Boolean) -> Unit>(relaxed = true)

		val initialState = InitialSyncOperationState(false)
		val result = op.execute(
			state = initialState,
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
		)

		assertTrue(isSuccess(result))
		assertEquals(initialState, result.data)

		coVerify { idAllocator.findNextId() }
		coVerify { syncJournal.createSyncData() }

		mockSynchronizers.synchronizers.forEach { synchronizer ->
			coVerify { synchronizer.prepareForSync() }
		}
	}
}