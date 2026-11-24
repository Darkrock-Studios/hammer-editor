package repositories.nux

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.nux.NuxFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.nux.NuxHint
import com.darkrockstudios.apps.hammer.common.data.nux.NuxRepository
import com.darkrockstudios.apps.hammer.common.data.nux.NuxProgress
import com.darkrockstudios.apps.hammer.common.data.nux.NuxState
import com.darkrockstudios.apps.hammer.common.data.nux.NuxPlatform
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Sink
import okio.Source
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import utils.FixedRandom
import utils.inMemoryGlobalSettingsStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.random.Random

class NuxRepositoryTest : BaseTest() {

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var globalSettingsStore: GlobalSettingsStore

	private val allHints = NuxHint.entries.toList()

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()
		fileSystem = FakeFileSystem()
		globalSettingsStore = inMemoryGlobalSettingsStore()
	}

	private fun createRepository(
		fs: FileSystem = fileSystem,
		random: Random = FixedRandom.losing(),
		platform: NuxPlatform = NuxPlatform.Desktop,
	) = NuxRepository(
		NuxFilesystemDatasource(fs, createTomlSerializer()),
		globalSettingsStore,
		platform = platform,
		random = random,
	)

	/** A screen's hints as the scene list asks for them: both menu tips, then reorder. */
	private val screenHints = listOf(NuxHint.SceneListSwipeMenu, NuxHint.SceneListContextMenu, NuxHint.SceneListReorder)

	private suspend fun NuxRepository.unseenNow() = unseen(allHints).first()

	private fun storedState() = NuxFilesystemDatasource(fileSystem, createTomlSerializer()).load()

	@Test
	fun `A fresh install shows every hint and writes nothing`() = runTest(mainTestDispatcher) {
		val repository = createRepository()

		assertEquals(allHints, repository.unseenNow())
		assertFalse(fileSystem.exists(NuxFilesystemDatasource.STATE_PATH))
	}

	@Test
	fun `A seen hint stays seen after a restart, and others are unaffected`() = runTest(mainTestDispatcher) {
		createRepository().markSeen(NuxHint.SceneListSwipeMenu)
		advanceUntilIdle()

		val restarted = createRepository()

		assertEquals(allHints - NuxHint.SceneListSwipeMenu, restarted.unseenNow())
	}

	@Test
	fun `A hint marked seen is hidden at once, before it is written`() = runTest(mainTestDispatcher) {
		val repository = createRepository()
		repository.unseenNow()

		repository.markSeen(NuxHint.SceneListSwipeMenu)
		val unseen = repository.unseen(allHints).first()

		assertEquals(allHints - NuxHint.SceneListSwipeMenu, unseen)
	}

	@Test
	fun `A hint marked seen before the file is read is not lost`() = runTest(mainTestDispatcher) {
		val repository = createRepository()

		repository.markSeen(NuxHint.SceneListReorder)
		advanceUntilIdle()

		assertEquals(allHints - NuxHint.SceneListReorder, repository.unseenNow())
		assertEquals(setOf(NuxHint.SceneListReorder.id), storedState().seenHints)
	}

	@Test
	fun `Unseen hints keep the order they were asked for in`() = runTest(mainTestDispatcher) {
		val repository = createRepository()

		val unseen = repository.unseen(allHints.reversed()).first()

		assertEquals(allHints.reversed(), unseen)
	}

	@Test
	fun `Turning tips off hides unseen hints without marking them seen`() = runTest(mainTestDispatcher) {
		val repository = createRepository()

		globalSettingsStore.updateSettings { it.copy(tipsEnabled = false) }
		val whileOff = repository.unseenNow()
		globalSettingsStore.updateSettings { it.copy(tipsEnabled = true) }

		assertTrue(whileOff.isEmpty())
		assertEquals(allHints, repository.unseenNow())
	}

	@Test
	fun `Resetting hints shows them again, after a restart too`() = runTest(mainTestDispatcher) {
		val repository = createRepository()
		repository.markSeen(NuxHint.SceneListSwipeMenu)
		advanceUntilIdle()

		repository.resetHints()
		advanceUntilIdle()

		assertEquals(allHints, repository.unseenNow())
		assertEquals(allHints, createRepository().unseenNow())
	}

	@Test
	fun `Resetting hints while tips are off turns tips back on`() = runTest(mainTestDispatcher) {
		val repository = createRepository()
		repository.markSeen(NuxHint.SceneListSwipeMenu)
		globalSettingsStore.updateSettings { it.copy(tipsEnabled = false) }

		repository.resetHints()
		advanceUntilIdle()

		assertTrue(globalSettingsStore.globalSettings.tipsEnabled)
		assertEquals(allHints, repository.unseenNow())
	}

	@Test
	fun `Hint ids this build does not know survive a write`() = runTest(mainTestDispatcher) {
		NuxFilesystemDatasource(fileSystem, createTomlSerializer())
			.store(NuxState(seenHints = setOf("from.a.newer.build")))

		createRepository().markSeen(NuxHint.SceneListReorder)
		advanceUntilIdle()

		assertEquals(setOf("from.a.newer.build", NuxHint.SceneListReorder.id), storedState().seenHints)
	}

	@Test
	fun `A corrupt state file starts over instead of failing`() = runTest(mainTestDispatcher) {
		fileSystem.createDirectories(NuxFilesystemDatasource.STATE_PATH.parent!!)
		fileSystem.write(NuxFilesystemDatasource.STATE_PATH) { writeUtf8("seenHints = [[[ not toml") }

		val repository = createRepository()

		assertEquals(allHints, repository.unseenNow())
	}

	@Test
	fun `An unreadable state file starts over instead of failing`() = runTest(mainTestDispatcher) {
		NuxFilesystemDatasource(fileSystem, createTomlSerializer())
			.store(NuxState(seenHints = setOf(NuxHint.SceneListReorder.id)))

		val repository = createRepository(FailingFileSystem(fileSystem, failReads = true))

		assertEquals(allHints, repository.unseenNow())
	}

	@Test
	fun `A failed write is survived and the hint stays hidden for the session`() = runTest(mainTestDispatcher) {
		val repository = createRepository(FailingFileSystem(fileSystem, failWrites = true))
		repository.unseenNow()

		repository.markSeen(NuxHint.SceneListSwipeMenu)
		repository.resetHints()
		repository.markSeen(NuxHint.SceneListReorder)
		advanceUntilIdle()

		assertEquals(allHints - NuxHint.SceneListReorder, repository.unseenNow())
		assertFalse(fileSystem.exists(NuxFilesystemDatasource.STATE_PATH))
	}

	@Test
	fun `A first visit offers every hint that applies here, whatever the roll`() = runTest(mainTestDispatcher) {
		val repository = createRepository(random = FixedRandom.losing(), platform = NuxPlatform.Desktop)

		val offered = repository.visitHints(screenHints, repository.startVisit()).first()

		assertEquals(listOf(NuxHint.SceneListContextMenu, NuxHint.SceneListReorder), offered)
	}

	@Test
	fun `A first visit on Android offers the swipe tip instead`() = runTest(mainTestDispatcher) {
		val repository = createRepository(platform = NuxPlatform.Android)

		val offered = repository.visitHints(screenHints, repository.startVisit()).first()

		assertEquals(listOf(NuxHint.SceneListSwipeMenu, NuxHint.SceneListReorder), offered)
	}

	@Test
	fun `A later visit offers the remaining hints on a winning roll`() = runTest(mainTestDispatcher) {
		val repository = createRepository(random = FixedRandom.winning())
		repository.markSeen(NuxHint.SceneListContextMenu)

		val offered = repository.visitHints(screenHints, repository.startVisit()).first()

		assertEquals(listOf(NuxHint.SceneListReorder), offered)
	}

	@Test
	fun `A later visit offers nothing on a losing roll`() = runTest(mainTestDispatcher) {
		val repository = createRepository(random = FixedRandom.losing())
		repository.markSeen(NuxHint.SceneListContextMenu)

		val offered = repository.visitHints(screenHints, repository.startVisit()).first()

		assertTrue(offered.isEmpty())
	}

	@Test
	fun `A visit never offers a hint for another platform`() = runTest(mainTestDispatcher) {
		val repository = createRepository(random = FixedRandom.winning(), platform = NuxPlatform.Desktop)
		repository.markSeen(NuxHint.SceneListContextMenu)
		repository.markSeen(NuxHint.SceneListReorder)

		val offered = repository.visitHints(screenHints, repository.startVisit()).first()

		assertTrue(offered.isEmpty())
	}

	@Test
	fun `After a reset a visit offers again as a first visit does`() = runTest(mainTestDispatcher) {
		val repository = createRepository(random = FixedRandom.losing())
		repository.markSeen(NuxHint.SceneListContextMenu)
		advanceUntilIdle()

		repository.resetHints()
		advanceUntilIdle()
		val offered = repository.visitHints(screenHints, repository.startVisit()).first()

		assertEquals(listOf(NuxHint.SceneListContextMenu, NuxHint.SceneListReorder), offered)
	}

	@Test
	fun `Turning tips off and on within a visit does not roll again`() = runTest(mainTestDispatcher) {
		val random = FixedRandom(FixedRandom.LOSE, FixedRandom.WIN)
		val repository = createRepository(random = random)
		repository.markSeen(NuxHint.SceneListContextMenu)
		val offers = mutableListOf<List<NuxHint>>()
		val visit = repository.visitHints(screenHints, repository.startVisit())
		val collecting = launch { visit.toList(offers) }
		advanceUntilIdle()

		globalSettingsStore.updateSettings { it.copy(tipsEnabled = false) }
		advanceUntilIdle()
		globalSettingsStore.updateSettings { it.copy(tipsEnabled = true) }
		advanceUntilIdle()
		collecting.cancel()

		assertEquals(listOf(emptyList<NuxHint>()), offers)
		assertEquals(1, random.rolls)
	}

	@Test
	fun `Progress counts only the hints that can show here`() = runTest(mainTestDispatcher) {
		val desktop = createRepository(platform = NuxPlatform.Desktop)
		desktop.markSeen(NuxHint.SceneListSwipeMenu)
		desktop.markSeen(NuxHint.SceneListContextMenu)

		assertEquals(NuxProgress(seen = 1, total = 2), desktop.progress().first())
		assertEquals(NuxProgress(seen = 0, total = 1), createRepository(platform = NuxPlatform.Ios).progress().first())
	}

	@Test
	fun `Progress ignores hint ids this build does not know`() = runTest(mainTestDispatcher) {
		NuxFilesystemDatasource(fileSystem, createTomlSerializer())
			.store(NuxState(seenHints = setOf("from.a.newer.build", NuxHint.SceneListReorder.id)))

		val progress = createRepository(platform = NuxPlatform.Android).progress().first()

		assertEquals(NuxProgress(seen = 1, total = 2), progress)
	}

	@Test
	fun `Progress follows dismissals and resets`() = runTest(mainTestDispatcher) {
		val repository = createRepository(platform = NuxPlatform.Desktop)
		val updates = mutableListOf<NuxProgress>()
		val collecting = launch { repository.progress().toList(updates) }
		advanceUntilIdle()

		repository.markSeen(NuxHint.SceneListReorder)
		advanceUntilIdle()
		repository.resetHints()
		advanceUntilIdle()
		collecting.cancel()

		assertEquals(listOf(NuxProgress(0, 2), NuxProgress(1, 2), NuxProgress(0, 2)), updates)
	}

	@Test
	fun `Progress counts tips seen while tips are off`() = runTest(mainTestDispatcher) {
		val repository = createRepository(platform = NuxPlatform.Desktop)
		repository.markSeen(NuxHint.SceneListReorder)

		globalSettingsStore.updateSettings { it.copy(tipsEnabled = false) }

		assertEquals(NuxProgress(seen = 1, total = 2), repository.progress().first())
	}

	@Test
	fun `Creating the repository does not touch the state file`() = runTest(mainTestDispatcher) {
		val recording = FailingFileSystem(fileSystem)

		createRepository(recording)

		assertEquals(0, recording.accesses)
	}
}

/** Counts every file access, and fails reads or writes on request, as a full or locked disk would. */
private class FailingFileSystem(
	delegate: FileSystem,
	private val failReads: Boolean = false,
	private val failWrites: Boolean = false,
) : ForwardingFileSystem(delegate) {
	var accesses = 0
		private set

	override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
		accesses++
		return path
	}

	override fun source(file: Path): Source {
		if (failReads) throw IOException("read failed")
		return super.source(file)
	}

	override fun sink(file: Path, mustCreate: Boolean): Sink {
		if (failWrites) throw IOException("write failed")
		return super.sink(file, mustCreate)
	}
}
