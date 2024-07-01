package com.darkrockstudios.apps.hammer.projects.repository

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ProjectsRepositoryCreateUserDataTest : ProjectsRepositoryBaseTest() {

	@Test
	fun `Create User Data`() = runTest {

		every { projectsDatasource.createUserData(userId) } just Runs

		createProjectsRepository().apply {
			createUserData(userId)
			verify { projectsDatasource.createUserData(userId) }
		}
	}
}