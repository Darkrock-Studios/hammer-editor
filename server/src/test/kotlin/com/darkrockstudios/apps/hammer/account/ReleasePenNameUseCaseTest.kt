package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReleasePenNameUseCaseTest {

	@MockK
	private lateinit var accountsRepository: AccountsRepository

	@MockK
	private lateinit var projectAccessRepository: ProjectAccessRepository

	private val userId = 1L

	@BeforeEach
	fun setup() {
		MockKAnnotations.init(this, relaxUnitFun = true)
	}

	@Test
	fun `clears the pen name and deletes all access`() = runTest {
		ReleasePenNameUseCase(accountsRepository, projectAccessRepository)(userId)

		coVerify { accountsRepository.updatePenName(userId, null) }
		coVerify { projectAccessRepository.deleteAllAccessForUser(userId) }
	}
}
