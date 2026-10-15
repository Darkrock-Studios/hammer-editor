package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

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
	fun `deletes all access, then clears the pen name`() = runTest {
		ReleasePenNameUseCase(accountsRepository, projectAccessRepository)(userId)

		coVerifyOrder {
			projectAccessRepository.deleteAllAccessForUser(userId)
			accountsRepository.updatePenName(userId, null)
		}
	}

	@Test
	fun `keeps the pen name when access can't be deleted`() = runTest {
		coEvery { projectAccessRepository.deleteAllAccessForUser(userId) } throws IllegalStateException("db down")

		assertFailsWith<IllegalStateException> {
			ReleasePenNameUseCase(accountsRepository, projectAccessRepository)(userId)
		}

		coVerify(exactly = 0) { accountsRepository.updatePenName(any(), any()) }
	}
}
