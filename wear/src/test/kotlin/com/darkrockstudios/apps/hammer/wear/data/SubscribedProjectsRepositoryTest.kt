package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.wear.FakeWearPrefsDatasource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscribedProjectsRepositoryTest {

	private val repository = SubscribedProjectsRepository(FakeWearPrefsDatasource())

	@Test
	fun `subscribing and unsubscribing is reflected in the current subscriptions`() = runTest {
		repository.setSubscribed(ProjectId("a"), true)
		repository.setSubscribed(ProjectId("b"), true)
		repository.setSubscribed(ProjectId("a"), false)

		assertEquals(setOf(ProjectId("b")), repository.currentSubscriptions())
	}

	@Test
	fun `clearing forgets every subscription`() = runTest {
		repository.setSubscribed(ProjectId("a"), true)

		repository.clear()

		assertEquals(emptySet<ProjectId>(), repository.currentSubscriptions())
	}
}
