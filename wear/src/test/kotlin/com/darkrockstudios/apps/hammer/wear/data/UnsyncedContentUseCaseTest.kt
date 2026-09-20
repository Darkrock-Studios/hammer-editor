package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.wear.FakeUnsyncedContentSource
import com.darkrockstudios.apps.hammer.wear.FakeWearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.TestProjects
import com.darkrockstudios.apps.hammer.wear.WearTestBase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UnsyncedContentUseCaseTest : WearTestBase() {

	private lateinit var projects: TestProjects
	private lateinit var subscriptions: SubscribedProjectsRepository
	private lateinit var source: FakeUnsyncedContentSource
	private lateinit var useCase: UnsyncedContentUseCase

	@BeforeEach
	override fun setUp() {
		super.setUp()
		projects = TestProjects()
		subscriptions = SubscribedProjectsRepository(FakeWearPrefsDatasource())
		source = FakeUnsyncedContentSource()
		useCase = UnsyncedContentUseCase(
			ListWatchProjectsUseCase(projects.repository, subscriptions),
			source,
		)
	}

	@Test
	fun `a watch holding nothing outstanding reports empty`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		subscriptions.setSubscribed(ProjectId("a"), true)

		val pending = useCase.pending()

		assertTrue(pending.isEmpty)
		assertEquals(0, pending.total)
	}

	@Test
	fun `notes and ideas are counted together`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		subscriptions.setSubscribed(ProjectId("a"), true)
		source.pendingByProject["Alpha"] = 2
		source.pendingIdeas = 3

		val pending = useCase.pending()

		assertFalse(pending.isEmpty)
		assertEquals(mapOf("Alpha" to 2), pending.projects)
		assertEquals(5, pending.total)
	}

	@Test
	fun `projects that are not on the watch are not counted`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		projects.create("Beta", serverId = "b")
		subscriptions.setSubscribed(ProjectId("a"), true)
		// Beta holds no content on the watch, so a stale count for it must not be reported.
		source.pendingByProject["Beta"] = 9

		val pending = useCase.pending()

		assertTrue(pending.isEmpty)
	}

	@Test
	fun `a project with nothing outstanding is left out of the breakdown`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		projects.create("Beta", serverId = "b")
		subscriptions.setSubscribed(ProjectId("a"), true)
		subscriptions.setSubscribed(ProjectId("b"), true)
		source.pendingByProject["Beta"] = 1

		val pending = useCase.pending()

		assertEquals(mapOf("Beta" to 1), pending.projects)
	}
}
