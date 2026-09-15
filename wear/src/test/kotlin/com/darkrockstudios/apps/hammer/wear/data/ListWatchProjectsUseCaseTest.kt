package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.wear.FakeWearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.TestProjects
import com.darkrockstudios.apps.hammer.wear.WearTestBase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ListWatchProjectsUseCaseTest : WearTestBase() {

	private lateinit var projects: TestProjects
	private lateinit var subscriptions: SubscribedProjectsRepository
	private lateinit var useCase: ListWatchProjectsUseCase

	@BeforeEach
	override fun setUp() {
		super.setUp()
		projects = TestProjects()
		subscriptions = SubscribedProjectsRepository(FakeWearPrefsDatasource())
		useCase = ListWatchProjectsUseCase(projects.repository, subscriptions)
	}

	@Test
	fun `a project is subscribed only when its server id is`() = runTest(dispatcher) {
		projects.create("Alpha", serverId = "a")
		projects.create("Beta", serverId = "b")
		subscriptions.setSubscribed(ProjectId("a"), true)

		val listed = useCase.list().associateBy { it.projectDef.name }

		assertTrue(listed.getValue("Alpha").subscribed)
		assertFalse(listed.getValue("Beta").subscribed)
	}

	@Test
	fun `a project the server does not know yet has no id and is never subscribed`() = runTest(dispatcher) {
		projects.create("Draft")

		val draft = useCase.list().single()

		assertEquals(null, draft.projectId)
		assertFalse(draft.subscribed)
	}

	@Test
	fun `projects are listed by name regardless of case`() = runTest(dispatcher) {
		projects.create("beta", serverId = "b")
		projects.create("Alpha", serverId = "a")
		projects.create("Charlie", serverId = "c")

		assertEquals(listOf("Alpha", "beta", "Charlie"), useCase.list().map { it.projectDef.name })
	}
}
