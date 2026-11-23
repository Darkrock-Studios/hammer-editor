package repositories.nux

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.nux.NuxFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.nux.NuxHint
import com.darkrockstudios.apps.hammer.common.data.nux.NuxRepository
import com.darkrockstudios.apps.hammer.common.data.nux.NuxState
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import kotlinx.coroutines.flow.first
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
import utils.inMemoryGlobalSettingsStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

	private fun createRepository(fs: FileSystem = fileSystem) = NuxRepository(
		NuxFilesystemDatasource(fs, createTomlSerializer()),
		globalSettingsStore,
	)

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

		assertEquals(listOf(NuxHint.SceneListReorder), restarted.unseenNow())
	}

	@Test
	fun `A hint marked seen is hidden at once, before it is written`() = runTest(mainTestDispatcher) {
		val repository = createRepository()
		repository.unseenNow()

		repository.markSeen(NuxHint.SceneListSwipeMenu)
		val unseen = repository.unseen(allHints).first()

		assertEquals(listOf(NuxHint.SceneListReorder), unseen)
	}

	@Test
	fun `A hint marked seen before the file is read is not lost`() = runTest(mainTestDispatcher) {
		val repository = createRepository()

		repository.markSeen(NuxHint.SceneListReorder)
		advanceUntilIdle()

		assertEquals(listOf(NuxHint.SceneListSwipeMenu), repository.unseenNow())
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

		assertEquals(listOf(NuxHint.SceneListSwipeMenu), repository.unseenNow())
		assertFalse(fileSystem.exists(NuxFilesystemDatasource.STATE_PATH))
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
