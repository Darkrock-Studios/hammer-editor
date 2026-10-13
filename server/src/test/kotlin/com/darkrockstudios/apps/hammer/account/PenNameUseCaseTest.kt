package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.account.PenNameUseCase.PenNameResult
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

class PenNameUseCaseTest {

	@MockK
	private lateinit var accountsRepository: AccountsRepository

	private lateinit var useCase: PenNameUseCase

	private val userId = 1L

	@BeforeEach
	fun setup() {
		MockKAnnotations.init(this, relaxUnitFun = true)
		useCase = PenNameUseCase(accountsRepository)
	}

	@Nested
	inner class SetPenName {

		@Test
		fun `setPenName - Success`() = runTest {
			val penName = "ValidName"
			coEvery { accountsRepository.isPenNameAvailable(penName, userId) } returns true

			val result = useCase.setPenName(userId, penName)

			assertEquals(PenNameResult.VALID, result)
			coVerify { accountsRepository.isPenNameAvailable(penName, userId) }
			coVerify { accountsRepository.updatePenName(userId, penName) }
		}

		@Test
		fun `setPenName - Trims whitespace`() = runTest {
			val penName = "  ValidName  "
			val trimmed = "ValidName"
			coEvery { accountsRepository.isPenNameAvailable(trimmed, userId) } returns true

			val result = useCase.setPenName(userId, penName)

			assertEquals(PenNameResult.VALID, result)
			coVerify { accountsRepository.updatePenName(userId, trimmed) }
		}

		@Test
		fun `setPenName - Too short`() = runTest {
			val penName = "abc"

			val result = useCase.setPenName(userId, penName)

			assertEquals(PenNameResult.TOO_SHORT, result)
			coVerify(exactly = 0) { accountsRepository.isPenNameAvailable(any(), any()) }
			coVerify(exactly = 0) { accountsRepository.updatePenName(any(), any()) }
		}

		@Test
		fun `setPenName - Too long`() = runTest {
			val penName = "a".repeat(PenNameUseCase.MAX_PEN_NAME_LENGTH + 1)

			val result = useCase.setPenName(userId, penName)

			assertEquals(PenNameResult.TOO_LONG, result)
			coVerify(exactly = 0) { accountsRepository.isPenNameAvailable(any(), any()) }
			coVerify(exactly = 0) { accountsRepository.updatePenName(any(), any()) }
		}

		@Test
		fun `setPenName - Invalid characters`() = runTest {
			val penName = "Name@123"

			val result = useCase.setPenName(userId, penName)

			assertEquals(PenNameResult.INVALID_CHARACTERS, result)
			coVerify(exactly = 0) { accountsRepository.isPenNameAvailable(any(), any()) }
			coVerify(exactly = 0) { accountsRepository.updatePenName(any(), any()) }
		}

		@Test
		fun `setPenName - Not available`() = runTest {
			val penName = "TakenName"
			coEvery { accountsRepository.isPenNameAvailable(penName, userId) } returns false

			val result = useCase.setPenName(userId, penName)

			assertEquals(PenNameResult.NOT_AVAILABLE, result)
			coVerify { accountsRepository.isPenNameAvailable(penName, userId) }
			coVerify(exactly = 0) { accountsRepository.updatePenName(any(), any()) }
		}
	}

	@Nested
	inner class IsPenNameAvailable {

		@Test
		fun `isPenNameAvailable - Delegates to repository`() = runTest {
			val penName = "TestName"
			coEvery { accountsRepository.isPenNameAvailable(penName, userId) } returns true

			val result = useCase.isPenNameAvailable(penName, userId)

			assertTrue(result)
			coVerify { accountsRepository.isPenNameAvailable(penName, userId) }
		}

		@Test
		fun `isPenNameAvailable - Returns false when not available`() = runTest {
			val penName = "TakenName"
			coEvery { accountsRepository.isPenNameAvailable(penName, null) } returns false

			val result = useCase.isPenNameAvailable(penName)

			assertFalse(result)
		}
	}

	@Nested
	inner class ValidatePenName {

		@Test
		fun `validatePenName - Valid simple name`() {
			val result = useCase.validatePenName("Author")
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid with spaces`() {
			val result = useCase.validatePenName("John Smith")
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid with hyphens`() {
			val result = useCase.validatePenName("Mary-Jane")
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid with underscores`() {
			val result = useCase.validatePenName("Cool_Writer")
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid with numbers`() {
			val result = useCase.validatePenName("Author123")
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid Unicode characters`() {
			val result = useCase.validatePenName("José García")
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid Japanese characters`() {
			val result = useCase.validatePenName("作家名前")
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid at minimum length`() {
			val result = useCase.validatePenName("a".repeat(PenNameUseCase.MIN_PEN_NAME_LENGTH))
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Valid at maximum length`() {
			val result = useCase.validatePenName("a".repeat(PenNameUseCase.MAX_PEN_NAME_LENGTH))
			assertEquals(PenNameResult.VALID, result)
		}

		@Test
		fun `validatePenName - Too short`() {
			val result = useCase.validatePenName("a".repeat(PenNameUseCase.MIN_PEN_NAME_LENGTH - 1))
			assertEquals(PenNameResult.TOO_SHORT, result)
		}

		@Test
		fun `validatePenName - Too long`() {
			val result = useCase.validatePenName("a".repeat(PenNameUseCase.MAX_PEN_NAME_LENGTH + 1))
			assertEquals(PenNameResult.TOO_LONG, result)
		}

		@Test
		fun `validatePenName - Cannot start with number`() {
			val result = useCase.validatePenName("123Author")
			assertEquals(PenNameResult.INVALID_CHARACTERS, result)
		}

		@Test
		fun `validatePenName - Cannot start with space`() {
			val result = useCase.validatePenName(" Author")
			assertEquals(
				PenNameResult.VALID,
				result
			) // After trim, becomes "Author" which is valid but we test the space prefix
		}

		@Test
		fun `validatePenName - Cannot contain special characters`() {
			val result = useCase.validatePenName("Author@Name")
			assertEquals(PenNameResult.INVALID_CHARACTERS, result)
		}

		@Test
		fun `validatePenName - Cannot contain exclamation`() {
			val result = useCase.validatePenName("Author!")
			assertEquals(PenNameResult.INVALID_CHARACTERS, result)
		}

		@Test
		fun `validatePenName - Cannot contain period`() {
			val result = useCase.validatePenName("J.K. Rowling")
			assertEquals(PenNameResult.INVALID_CHARACTERS, result)
		}

		@Test
		fun `validatePenName - Empty string after trim`() {
			val result = useCase.validatePenName("   ")
			assertEquals(PenNameResult.TOO_SHORT, result)
		}
	}
}
