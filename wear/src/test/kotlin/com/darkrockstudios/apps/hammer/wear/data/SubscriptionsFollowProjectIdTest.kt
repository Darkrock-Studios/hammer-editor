package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.wear.FakeWearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.TestProjects
import com.darkrockstudios.apps.hammer.wear.WearTestBase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionsFollowProjectIdTest : WearTestBase() {

	private lateinit var projects: TestProjects
	private lateinit var subscriptions: SubscribedProjectsRepository

	@BeforeEach
	override fun setUp() {
		super.setUp()
		projects = TestProjects()
		subscriptions = SubscribedProjectsRepository(FakeWearPrefsDatasource())
		subscriptions.followProjectIdChanges(projects.repository)
	}

	@Test
	fun `an id change moves the subscription and the last capture target`() = runTest(dispatcher) {
		val alpha = projects.create("Alpha", projectId = "minted-on-the-watch")
		projects.create("Beta", projectId = "b")
		subscriptions.setSubscribed(ProjectId("minted-on-the-watch"), true)
		subscriptions.setSubscribed(ProjectId("b"), true)
		subscriptions.setLastCaptureProject(ProjectId("minted-on-the-watch"))

		projects.repository.setProjectId(alpha, ProjectId("adopted-from-the-server"))

		assertEquals(setOf(ProjectId("adopted-from-the-server"), ProjectId("b")), subscriptions.currentSubscriptions())
		assertEquals(ProjectId("adopted-from-the-server"), subscriptions.lastCaptureProjectId())
	}

	@Test
	fun `an unsubscribed project stays unsubscribed after an id change`() = runTest(dispatcher) {
		val alpha = projects.create("Alpha", projectId = "a")
		subscriptions.setLastCaptureProject(ProjectId("b"))

		projects.repository.setProjectId(alpha, ProjectId("a2"))

		assertEquals(emptySet<ProjectId>(), subscriptions.currentSubscriptions())
		assertEquals(ProjectId("b"), subscriptions.lastCaptureProjectId())
	}

	@Test
	fun `a re-minted duplicate starts unsubscribed and the original keeps its subscription`() = runTest(dispatcher) {
		projects.create("Alpha", projectId = "shared")
		projects.create("Alpha copy", projectId = "shared")
		subscriptions.setSubscribed(ProjectId("shared"), true)
		subscriptions.setLastCaptureProject(ProjectId("shared"))

		projects.repository.resolveDuplicateProjectIds()

		val ids = projects.repository.getProjects().map { projects.repository.getProjectId(it) }.toSet()
		assertEquals(2, ids.size)
		assertEquals(setOf(ProjectId("shared")), subscriptions.currentSubscriptions())
		assertEquals(ProjectId("shared"), subscriptions.lastCaptureProjectId())
	}
}
