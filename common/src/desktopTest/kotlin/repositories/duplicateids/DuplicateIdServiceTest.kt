package repositories.duplicateids

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.createProjectScope
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdService
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdService.ResolveResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.id.datasources.EncyclopediaIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.NotesIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.ImageIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneDraftIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.TimeLineEventIdDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.scope.Scope
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class DuplicateIdServiceTest : RationalizerTestBase() {

	private class FakeBackups(private val succeeds: Boolean) :
		ProjectBackupService(FakeFileSystem(), mockk(), mockk(), mockk(), mockk(), mockk()) {
		var backups = 0

		override fun supportsBackup(): Boolean = true

		override suspend fun createBackup(projectDef: ProjectDef): ProjectBackupDef? {
			if (!succeeds) return null
			backups++
			return ProjectBackupDef(projectDef.path, projectDef.name, Instant.fromEpochSeconds(0))
		}
	}

	private lateinit var syncJournal: SyncJournal
	private lateinit var sceneRepository: SceneRepository
	private lateinit var encyclopediaRepository: EncyclopediaRepository
	private lateinit var projectScope: Scope

	@BeforeEach
	override fun setup() {
		super.setup()
		syncJournal = mockk(relaxed = true)
		sceneRepository = mockk(relaxed = true)
		encyclopediaRepository = mockk(relaxed = true)
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped { projectDef }
				scoped { syncJournal }
				scoped { sceneRepository }
				scoped { encyclopediaRepository }
				scoped<NotesRepository> { mockk(relaxed = true) }
				scoped { SceneDatasource(projectDef, ffs) }
				scoped { SceneDraftsDatasource(projectDef, ffs) }
				factory { SceneIdDatasource(ffs) }
				factory { NotesIdDatasource(ffs) }
				factory { EncyclopediaIdDatasource(ffs) }
				factory { TimeLineEventIdDatasource(ffs, toml) }
				factory { SceneDraftIdDatasource(ffs) }
				factory { ImageIdDatasource(ffs) }
				scoped { IdAllocator(projectDef) }
			}
		})
		projectScope = createProjectScope(projectDef)
	}

	private fun service(backups: ProjectBackupService) =
		DuplicateIdService(projectDef, rationalizer, syncJournal, backups)

	private fun writeConflictingEntries() {
		write("encyclopedia/person/person~5~Bob.toml", entryToml(5, "Bob", "The real Bob"))
		write("encyclopedia/person/person~5~Bob.sync-conflict-x.toml", entryToml(5, "Bob", "Laptop Bob"))
	}

	@Test
	fun `Copies left after open wait for the user and block sync`() = runTest {
		writeConflictingEntries()
		val service = service(FakeBackups(succeeds = true))

		service.rationalizeOnOpen()

		assertEquals(5, service.unresolved.value.single().id)
		assertTrue(service.hasDuplicates())
	}

	@Test
	fun `A failed backup deletes nothing`() = runTest {
		writeConflictingEntries()
		val service = service(FakeBackups(succeeds = false))
		service.rationalizeOnOpen()
		val set = service.unresolved.value.single()

		val result = service.resolve(set, keep = setOf(set.inUse.path))

		assertEquals(ResolveResult.BackupFailed, result)
		assertEquals(2, filesIn("encyclopedia").size)
		assertEquals(1, service.unresolved.value.size)
	}

	@Test
	fun `Keeping a copy other than the one in use marks it for upload`() = runTest {
		writeConflictingEntries()
		val backups = FakeBackups(succeeds = true)
		val service = service(backups)
		service.rationalizeOnOpen()
		val set = service.unresolved.value.single()

		val result = service.resolve(set, keep = setOf(set.copies.last().path))

		assertEquals(ResolveResult.Resolved, result)
		assertEquals(1, backups.backups)
		assertEquals(listOf("person/person~5~Bob.toml"), filesIn("encyclopedia"))
		assertTrue(service.unresolved.value.isEmpty())
		assertFalse(service.hasDuplicates())
		coVerify { syncJournal.markEntityAsDirty(5) }
		verify { encyclopediaRepository.loadEntries() }
	}

	@Test
	fun `Keeping every copy needs no backup and gives the extra one a fresh id`() = runTest {
		write("scenes/0~Prologue~12.md", "Once upon a time")
		write("scenes/1~Duel~12.md", "En garde")
		write("notes/note-20.toml", noteToml(20, "x"))
		val backups = FakeBackups(succeeds = true)
		val service = service(backups)
		service.rationalizeOnOpen()
		projectScope.get<IdAllocator>().findNextId()
		val set = service.unresolved.value.single()

		val result = service.resolve(set, keep = set.copies.map { it.path }.toSet())

		assertEquals(ResolveResult.Resolved, result)
		assertEquals(0, backups.backups)
		assertEquals(listOf("0~Prologue~12.md", "1~Duel~21.md"), filesIn("scenes"))
		coVerify { sceneRepository.reloadTreeFromDisk() }
	}

	@Test
	fun `A set that changed on disk is refreshed instead of resolved`() = runTest {
		writeConflictingEntries()
		val service = service(FakeBackups(succeeds = true))
		service.rationalizeOnOpen()
		val set = service.unresolved.value.single()
		write("encyclopedia/person/person~5~Bob.sync-conflict-y.toml", entryToml(5, "Bob", "Phone Bob"))

		val result = service.resolve(set, keep = setOf(set.inUse.path))

		assertEquals(ResolveResult.Stale, result)
		assertEquals(3, filesIn("encyclopedia").size)
		assertEquals(3, service.unresolved.value.single().copies.size)
	}
}
