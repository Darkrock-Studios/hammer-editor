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
	fun `a project is subscribed only when its id is`() = runTest(dispatcher) {
		projects.create("Alpha", projectId = "a")
		projects.create("Beta", projectId = "b")
		subscriptions.setSubscribed(ProjectId("a"), true)

		val listed = useCase.list().associateBy { it.projectDef.name }

		assertTrue(listed.getValue("Alpha").subscribed)
		assertFalse(listed.getValue("Beta").subscribed)
	}

	@Test
	fun `a project the server does not know yet has its own id and can be subscribed`() = runTest(dispatcher) {
		val projectDef = projects.create("Draft")

		val draft = useCase.list().single()
		assertEquals(projectDef.id, draft.projectDef.id)
		assertFalse(draft.subscribed)

		subscriptions.setSubscribed(draft.projectDef.id, true)

		assertTrue(useCase.list().single().subscribed)
	}

	@Test
	fun `projects are listed by name regardless of case`() = runTest(dispatcher) {
		projects.create("beta", projectId = "b")
		projects.create("Alpha", projectId = "a")
		projects.create("Charlie", projectId = "c")

		assertEquals(listOf("Alpha", "beta", "Charlie"), useCase.list().map { it.projectDef.name })
	}
}
