package com.darkrockstudios.apps.hammer.projects.repository

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ProjectsRepositoryCreateUserDataTest : ProjectsRepositoryBaseTest() {

	@Test
	fun `Create User Data`() = runTest {

		coEvery { projectsDatasource.createUserData(userId) } just Runs

		createProjectsRepository().apply {
			createUserData(userId)
			coVerify { projectsDatasource.createUserData(userId) }
		}
	}
}