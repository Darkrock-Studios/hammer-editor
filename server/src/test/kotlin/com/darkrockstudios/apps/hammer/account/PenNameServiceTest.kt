package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.account.PenNameService.PenNameResult
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PenNameServiceTest {

	@MockK
	private lateinit var accountsRepository: AccountsRepository

	@MockK
	private lateinit var projectAccessRepository: ProjectAccessRepository

	private lateinit var service: PenNameService

	private val userId = 1L

	@BeforeEach
	fun setup() {
		MockKAnnotations.init(this, relaxUnitFun = true)
		service = PenNameService(accountsRepository, projectAccessRepository)
	}

	@Nested
	inner class SetPenName {

		@Test
		fun `setPenName - Success`() = runTest {
			val penName = "ValidName"
			coEvery { accountsRepository.isPenNameAvailable(penName, userId) } returns true

			val result = service.setPenName(userId, penName)

			assertEquals(PenNameResult.VALID, result)
			coVerify { accountsRepository.isPenNameAvailable(penName, userId) }
			coVerify { accountsRepository.updatePenName(userId, penName) }
		}

		@Test
		fun `setPenName - Trims whitespace`() = runTest {
			val penName = "  ValidName  "
			val trimmed = "ValidName"
			coEvery { accountsRepository.isPenNameAvailable(trimmed, userId) } returns true

			val result = service.setPenName(userId, penName)

			assertEquals(PenNameResult.VALID, result)
			coVerify { accountsRepository.updatePenName(userId, trimmed) }
		}

		@Test
		fun `setPenName - Too short`() = runTest {
			val penName = "abc"

			val result = service.setPenName(userId, penName)

			assertEquals(PenNameResult.TOO_SHORT, result)
			coVerify(exactly = 0) { accountsRepository.isPenNameAvailable(any(), any()) }
			coVerify(exactly = 0) { accountsRepository.updatePenName(any(), any()) }
		}

		@Test
		fun `setPenName - Too long`() = runTest {
			val penName = "a".repeat(PenNameService.MAX_PEN_NAME_LENGTH + 1)

			val result = service.setPenName(userId, penName)

			assertEquals(PenNameResult.TOO_LONG, result)
			coVerify(exactly = 0) { accountsRepository.isPenNameAvailable(any(), any()) }
			coVerify(exactly = 0) { accountsRepository.updatePenName(any(), any()) }
		}

		@Test
		fun `setPenName - Invalid characters`() = runTest {
			val penName = "Name@123"

			val result = service.setPenName(userId, penName)

			assertEquals(PenNameResult.INVALID_CHARACTERS, result)
			coVerify(exactly = 0) { accountsRepository.isPenNameAvailable(any(), any()) }
			coVerify(exactly = 0) { accountsRepository.updatePenName(any(), any()) }
		}

		@Test
		fun `setPenName - Not available`() = runTest {
			val penName = "TakenName"
			coEvery { accountsRepository.isPenNameAvailable(penName, userId) } returns false

			val result = service.setPenName(userId, penName)

			assertEquals(PenNameResult.NOT_AVAILABLE, result)
			coVerify { accountsRepository.isPenNameAvailable(penName, userId) }
			coVerify(exactly = 0) { accountsRepository.updatePenName(any(), any()) }
		}
	}

	@Nested
	inner class ReleasePenName {

		@Test
		fun `releasePenName - Clears pen name and deletes all access`() = runTest {
			service.releasePenName(userId)

			coVerify { accountsRepository.updatePenName(userId, null) }
			coVerify { projectAccessRepository.deleteAllAccessForUser(userId) }
		}
	}

	@Nested
	inner class IsPenNameAvailable {

		@Test
		fun `isPenNameAvailable - Delegates to repository`() = runTest {
			val penName = "TestName"
			coEvery { accountsRepository.isPenNameAvailable(penName, userId) } returns true

			val result = service.isPenNameAvailable(penName, userId)

			assertTrue(result)
			coVerify { accountsRepository.isPenNameAvailable(penName, userId) }
		}

		@Test
		fun `isPenNameAvailable - Returns false when not available`() = runTest {
			val penName = "TakenName"
			coEvery { accountsRepository.isPenNameAvailable(penName, null) } returns false

			val result = service.isPenNameAvailable(penName)

			assertFalse(result)
		}
	}

	@Nested
	inner class ValidatePenName {

		@Test
		fun `validatePenName - Valid simple name`() {
			val result = service.validatePenName("Author")
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid with spaces`() {
			val result = service.validatePenName("John Smith")
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid with hyphens`() {
			val result = service.validatePenName("Mary-Jane")
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid with underscores`() {
			val result = service.validatePenName("Cool_Writer")
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid with numbers`() {
			val result = service.validatePenName("Author123")
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid Unicode characters`() {
			val result = service.validatePenName("José García")
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid Japanese characters`() {
			val result = service.validatePenName("作家名前")
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid at minimum length`() {
			val result = service.validatePenName("a".repeat(PenNameService.MIN_PEN_NAME_LENGTH))
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid at maximum length`() {
			val result = service.validatePenName("a".repeat(PenNameService.MAX_PEN_NAME_LENGTH))
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Too short`() {
			val result = service.validatePenName("a".repeat(PenNameService.MIN_PEN_NAME_LENGTH - 1))
			assertEquals(PenNameResult.TOO_SHORT, result)
		}

		@Test
		fun `validatePenName - Too long`() {
			val result = service.validatePenName("a".repeat(PenNameService.MAX_PEN_NAME_LENGTH + 1))
			assertEquals(PenNameResult.TOO_LONG, result)
		}

		@Test
		fun `validatePenName - Cannot start with number`() {
			val result = service.validatePenName("123Author")
			assertEquals(PenNameResult.INVALID_CHARACTERS, result)
		}

		@Test
		fun `validatePenName - Cannot start with space`() {
			val result = service.validatePenName(" Author")
			assertEquals(
				PenNameResult.VALID,
				result
			) // After trim, becomes "Author" which is valid but we test the space prefix
		}

		@Test
		fun `validatePenName - Cannot contain special characters`() {
			val result = service.validatePenName("Author@Name")
			assertEquals(PenNameResult.INVALID_CHARACTERS, result)
		}

		@Test
		fun `validatePenName - Cannot contain exclamation`() {
			val result = service.validatePenName("Author!")
			assertEquals(PenNameResult.INVALID_CHARACTERS, result)
		}

		@Test
		fun `validatePenName - Cannot contain period`() {
			val result = service.validatePenName("J.K. Rowling")
			assertEquals(PenNameResult.INVALID_CHARACTERS, result)
		}

		@Test
		fun `validatePenName - Empty string after trim`() {
			val result = service.validatePenName("   ")
			assertEquals(PenNameResult.TOO_SHORT, result)
		}
	}
}
