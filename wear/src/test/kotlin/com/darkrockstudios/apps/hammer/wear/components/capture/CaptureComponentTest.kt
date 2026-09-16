package com.darkrockstudios.apps.hammer.wear.components.capture

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.wear.FakeCaptureSurfaceUpdater
import com.darkrockstudios.apps.hammer.wear.FakeCaptureSyncScheduler
import com.darkrockstudios.apps.hammer.wear.FakeCaptureWriter
import com.darkrockstudios.apps.hammer.wear.FakeUnsyncedContentSource
import com.darkrockstudios.apps.hammer.wear.FakeWearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.GatedUnsyncedContentSource
import com.darkrockstudios.apps.hammer.wear.TestProjects
import com.darkrockstudios.apps.hammer.wear.WearTestBase
import com.darkrockstudios.apps.hammer.wear.data.CaptureTargetsUseCase
import com.darkrockstudios.apps.hammer.wear.data.CaptureUseCase
import com.darkrockstudios.apps.hammer.wear.data.ListWatchProjectsUseCase
import com.darkrockstudios.apps.hammer.wear.data.SubscribedProjectsRepository
import com.darkrockstudios.apps.hammer.wear.data.UnsyncedContentUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CaptureComponentTest : WearTestBase() {

	private lateinit var projects: TestProjects
	private lateinit var subscriptions: SubscribedProjectsRepository
	private lateinit var writer: FakeCaptureWriter
	private lateinit var unsynced: FakeUnsyncedContentSource
	private lateinit var captureSync: FakeCaptureSyncScheduler
	private lateinit var surfaces: FakeCaptureSurfaceUpdater

	@BeforeEach
	override fun setUp() {
		super.setUp()
		projects = TestProjects()
		subscriptions = SubscribedProjectsRepository(FakeWearPrefsDatasource())
		writer = FakeCaptureWriter()
		unsynced = FakeUnsyncedContentSource()
		captureSync = FakeCaptureSyncScheduler()
		surfaces = FakeCaptureSurfaceUpdater()
	}

	private fun newComponent(mode: Capture.Mode, startWithPicker: Boolean = false): CaptureComponent {
		val listProjects = ListWatchProjectsUseCase(projects.repository, subscriptions)
		return CaptureComponent(
			componentContext = componentContext,
			mode = mode,
			startWithPicker = startWithPicker,
			captureTargets = CaptureTargetsUseCase(listProjects, subscriptions),
			subscriptions = subscriptions,
			captureUseCase = CaptureUseCase(
				writer = writer,
				unsyncedContent = UnsyncedContentUseCase(listProjects, unsynced),
				captureSync = captureSync,
			surfaces = surfaces,
			),
			appScope = CoroutineScope(dispatcher),
		).also {
			resumeLifecycle()
			scheduler.advanceUntilIdle()
		}
	}

	@Test
	fun `a note defaults to the project last captured to`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		projects.create("Beta", serverId = "b")
		subscriptions.setSubscribed(ProjectId("a"), true)
		subscriptions.setSubscribed(ProjectId("b"), true)
		subscriptions.setLastCaptureProject(ProjectId("b"))

		val component = newComponent(Capture.Mode.Note)

		assertEquals("Beta", component.state.value.projectName)
		assertEquals(listOf("Alpha", "Beta"), component.state.value.projects)
	}

	@Test
	fun `a first capture falls back to the first project on the watch`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		subscriptions.setSubscribed(ProjectId("a"), true)

		val component = newComponent(Capture.Mode.Note)

		assertEquals("Alpha", component.state.value.projectName)
	}

	@Test
	fun `projects that are not kept on the watch are not capture targets`() = runTest(dispatcher) {
		// A note in an unsynced project would take id 1 and collide on every later sync.
		projects.create("Alpha", serverId = "a")

		val component = newComponent(Capture.Mode.Note)

		assertEquals(emptyList<String>(), component.state.value.projects)
		assertEquals(Capture.Outcome.NoProjects, component.state.value.outcome)
	}

	@Test
	fun `an idea needs no project to be saveable`() = runTest(dispatcher) {
		val component = newComponent(Capture.Mode.Idea)

		component.onTextEntered("a town where everyone shares one memory")

		assertFalse(component.state.value.loading)
		assertNull(component.state.value.projectName)
		assertTrue(component.state.value.canSave)
	}

	@Test
	fun `a note cannot be saved before its text arrives`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		subscriptions.setSubscribed(ProjectId("a"), true)

		val component = newComponent(Capture.Mode.Note)

		assertFalse(component.state.value.canSave)

		component.onTextEntered("salt on the stairs")

		assertTrue(component.state.value.canSave)
	}

	@Test
	fun `saving a note writes it and reports what is waiting to sync`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		subscriptions.setSubscribed(ProjectId("a"), true)
		unsynced.pendingByProject["Alpha"] = 4
		val component = newComponent(Capture.Mode.Note)
		component.onTextEntered("salt on the stairs")

		component.save()
		scheduler.advanceUntilIdle()

		assertEquals(listOf("Alpha" to "salt on the stairs"), writer.notes)
		assertEquals(Capture.Outcome.Saved(pending = 4), component.state.value.outcome)
		assertFalse(component.state.value.saving)
		assertEquals(1, captureSync.requests)
	}

	@Test
	fun `saving an idea writes it`() = runTest(dispatcher) {
		val component = newComponent(Capture.Mode.Idea)
		component.onTextEntered("a town with one memory")

		component.save()
		scheduler.advanceUntilIdle()

		assertEquals(listOf("a town with one memory"), writer.ideas)
		assertEquals(Capture.Outcome.Saved(pending = 0), component.state.value.outcome)
	}

	@Test
	fun `a write that fails is reported rather than looking saved`() = runTest(dispatcher) {
		writer.succeed = false
		val component = newComponent(Capture.Mode.Idea)
		component.onTextEntered("a thought")

		component.save()
		scheduler.advanceUntilIdle()

		assertEquals(Capture.Outcome.Failed, component.state.value.outcome)
	}

	@Test
	fun `choosing a project remembers it for the next capture`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		projects.create("Beta", serverId = "b")
		subscriptions.setSubscribed(ProjectId("a"), true)
		subscriptions.setSubscribed(ProjectId("b"), true)
		val component = newComponent(Capture.Mode.Note)

		component.showProjectPicker()
		assertTrue(component.state.value.pickingProject)

		component.selectProject("Beta")
		scheduler.advanceUntilIdle()

		assertFalse(component.state.value.pickingProject)
		assertEquals("Beta", component.state.value.projectName)
		assertEquals(ProjectId("b"), subscriptions.lastCaptureProjectId())
	}

	@Test
	fun `the save is confirmed before the count is gathered`() = runTest(dispatcher) {
		val gate = CompletableDeferred<Unit>()
		val listProjects = ListWatchProjectsUseCase(projects.repository, subscriptions)
		val component = CaptureComponent(
			componentContext = componentContext,
			mode = Capture.Mode.Idea,
			captureTargets = CaptureTargetsUseCase(listProjects, subscriptions),
			subscriptions = subscriptions,
			captureUseCase = CaptureUseCase(
				writer = writer,
				unsyncedContent = UnsyncedContentUseCase(listProjects, GatedUnsyncedContentSource(gate)),
				captureSync = captureSync,
			surfaces = surfaces,
			),
			appScope = CoroutineScope(dispatcher),
		).also {
			resumeLifecycle()
			scheduler.advanceUntilIdle()
		}
		component.onTextEntered("a thought worth keeping")

		component.save()
		scheduler.advanceUntilIdle()

		// Counting is still blocked, but the user has already been told their words are safe.
		assertEquals(listOf("a thought worth keeping"), writer.ideas)
		assertEquals(Capture.Outcome.Saved(pending = null), component.state.value.outcome)

		gate.complete(Unit)
		scheduler.advanceUntilIdle()

		assertEquals(Capture.Outcome.Saved(pending = 0), component.state.value.outcome)
	}

	@Test
	fun `a count that cannot be read leaves the save confirmed`() = runTest(dispatcher) {
		unsynced.failWith = IllegalStateException("no journal")
		val component = newComponent(Capture.Mode.Idea)
		component.onTextEntered("a thought worth keeping")

		component.save()
		scheduler.advanceUntilIdle()

		assertEquals(Capture.Outcome.Saved(pending = null), component.state.value.outcome)
	}

	@Test
	fun `leaving the project picker keeps the current project`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		projects.create("Beta", serverId = "b")
		subscriptions.setSubscribed(ProjectId("a"), true)
		subscriptions.setSubscribed(ProjectId("b"), true)
		val component = newComponent(Capture.Mode.Note)
		component.showProjectPicker()

		component.dismissProjectPicker()
		scheduler.advanceUntilIdle()

		assertFalse(component.state.value.pickingProject)
		assertEquals("Alpha", component.state.value.projectName)
		assertNull(subscriptions.lastCaptureProjectId())
	}

	@Test
	fun `leaving with dictated text asks before dropping it`() = runTest(dispatcher) {
		val component = newComponent(Capture.Mode.Idea)
		component.onTextEntered("a town where everyone shares one memory")

		assertTrue(component.state.value.hasUnsavedWork)
		component.requestDiscard()

		assertTrue(component.state.value.confirmingDiscard)

		component.cancelDiscard()

		assertFalse(component.state.value.confirmingDiscard)
		assertEquals("a town where everyone shares one memory", component.state.value.text)
	}

	@Test
	fun `leaving with nothing dictated has nothing to ask about`() = runTest(dispatcher) {
		val component = newComponent(Capture.Mode.Idea)

		assertFalse(component.state.value.hasUnsavedWork)
		component.requestDiscard()

		assertFalse(component.state.value.confirmingDiscard)
	}

	@Test
	fun `a saved capture is not unsaved work`() = runTest(dispatcher) {
		val component = newComponent(Capture.Mode.Idea)
		component.onTextEntered("a thought worth keeping")
		component.save()
		scheduler.advanceUntilIdle()

		// The words are on disk, so the swipe out should just leave.
		assertFalse(component.state.value.hasUnsavedWork)
		component.requestDiscard()

		assertFalse(component.state.value.confirmingDiscard)
	}

	@Test
	fun `saving clears a confirmation left open behind it`() = runTest(dispatcher) {
		val component = newComponent(Capture.Mode.Idea)
		component.onTextEntered("a thought worth keeping")
		component.requestDiscard()

		component.save()
		scheduler.advanceUntilIdle()

		assertFalse(component.state.value.confirmingDiscard)
		assertEquals(Capture.Outcome.Saved(pending = 0), component.state.value.outcome)
	}

	@Test
	fun `the tile can send you straight to the project picker`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		projects.create("Beta", serverId = "b")
		subscriptions.setSubscribed(ProjectId("a"), true)
		subscriptions.setSubscribed(ProjectId("b"), true)

		val component = newComponent(Capture.Mode.Note, startWithPicker = true)

		assertTrue(component.state.value.pickingProject)
	}

	@Test
	fun `there is no picker to show when only one project is on the watch`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		subscriptions.setSubscribed(ProjectId("a"), true)

		val component = newComponent(Capture.Mode.Note, startWithPicker = true)

		assertFalse(component.state.value.pickingProject)
		assertEquals("Alpha", component.state.value.projectName)
	}

	@Test
	fun `saving twice does not write the capture twice`() = runTest(dispatcher) {
		val component = newComponent(Capture.Mode.Idea)
		component.onTextEntered("a thought worth keeping")

		component.save()
		component.save()
		scheduler.advanceUntilIdle()

		assertEquals(1, writer.ideas.size)
	}
}
