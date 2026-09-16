package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.wear.FakeCaptureSurfaceUpdater
import com.darkrockstudios.apps.hammer.wear.FakeCaptureSyncScheduler
import com.darkrockstudios.apps.hammer.wear.FakeCaptureWriter
import com.darkrockstudios.apps.hammer.wear.FakeUnsyncedContentSource
import com.darkrockstudios.apps.hammer.wear.FakeWearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.TestProjects
import com.darkrockstudios.apps.hammer.wear.WearTestBase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CaptureUseCaseTest : WearTestBase() {

	private lateinit var projects: TestProjects
	private lateinit var writer: FakeCaptureWriter
	private lateinit var unsynced: FakeUnsyncedContentSource
	private lateinit var captureSync: FakeCaptureSyncScheduler
	private lateinit var surfaces: FakeCaptureSurfaceUpdater
	private lateinit var useCase: CaptureUseCase

	@BeforeEach
	override fun setUp() {
		super.setUp()
		projects = TestProjects()
		writer = FakeCaptureWriter()
		unsynced = FakeUnsyncedContentSource()
		captureSync = FakeCaptureSyncScheduler()
		surfaces = FakeCaptureSurfaceUpdater()
		val subscriptions = SubscribedProjectsRepository(FakeWearPrefsDatasource())
		useCase = CaptureUseCase(
			writer = writer,
			unsyncedContent = UnsyncedContentUseCase(
				ListWatchProjectsUseCase(projects.repository, subscriptions),
				unsynced,
			),
			captureSync = captureSync,
			surfaces = surfaces,
		)
	}

	@Test
	fun `a dictated note is saved and pushed to the server`() = runTest(dispatcher) {
		val projectDef = projects.create("Alpha", serverId = "a")
		unsynced.pendingIdeas = 2

		val result = useCase.capture(CaptureTarget.Note(projectDef), "  salt on the stairs  ")

		assertEquals(listOf("Alpha" to "salt on the stairs"), writer.notes)
		assertEquals(CaptureResult.Saved, result)
		assertEquals(1, captureSync.requests)
		assertEquals(2, useCase.pendingCount())
	}

	@Test
	fun `an idea needs no project`() = runTest(dispatcher) {
		val result = useCase.capture(CaptureTarget.Idea, "a town with one memory")

		assertEquals(listOf("a town with one memory"), writer.ideas)
		assertEquals(CaptureResult.Saved, result)
		assertEquals(1, captureSync.requests)
	}

	@Test
	fun `a capture tells the watch face surfaces to re-read`() = runTest(dispatcher) {
		useCase.capture(CaptureTarget.Idea, "a thought worth keeping")

		// The tile and complication are cached, so without this they keep showing a stale count.
		assertEquals(1, surfaces.refreshes)
	}

	@Test
	fun `a capture that was not saved does not refresh anything`() = runTest(dispatcher) {
		writer.succeed = false

		useCase.capture(CaptureTarget.Idea, "a thought")

		assertEquals(0, surfaces.refreshes)
	}

	@Test
	fun `blank text is not a capture`() = runTest(dispatcher) {
		val result = useCase.capture(CaptureTarget.Idea, "   ")

		assertEquals(CaptureResult.Empty, result)
		assertEquals(emptyList<String>(), writer.ideas)
		assertEquals(0, captureSync.requests)
	}

	@Test
	fun `a rejected write is not reported as saved`() = runTest(dispatcher) {
		writer.succeed = false

		val result = useCase.capture(CaptureTarget.Idea, "too long, perhaps")

		assertEquals(CaptureResult.Failed, result)
		assertEquals(0, captureSync.requests)
	}

	@Test
	fun `a thrown write is not reported as saved`() = runTest(dispatcher) {
		writer.failWith = IllegalStateException("disk full")

		val result = useCase.capture(CaptureTarget.Idea, "a thought")

		assertEquals(CaptureResult.Failed, result)
		assertEquals(0, captureSync.requests)
	}

	@Test
	fun `a count that cannot be read never affects the capture`() = runTest(dispatcher) {
		val projectDef = projects.create("Alpha", serverId = ProjectId("a").id)
		unsynced.failWith = IllegalStateException("no journal")

		val result = useCase.capture(CaptureTarget.Note(projectDef), "a line worth keeping")

		assertEquals(CaptureResult.Saved, result)
		assertEquals(1, writer.notes.size)
		assertEquals(1, captureSync.requests)
		// Null, not zero: the user must not be told nothing is waiting when we could not look.
		assertNull(useCase.pendingCount())
	}
}
