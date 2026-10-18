package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.account.AdminPasswordResetResult
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidationResult
import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ResetAdminPasswordCommandTest {

	private class FakeResetter(
		private val result: AdminPasswordResetResult = AdminPasswordResetResult.Success("admin@example.com"),
		private val failure: Exception? = null,
	) : AdminPasswordResetter {
		var email: String? = null
		var password: String? = null
		var calls = 0

		override fun reset(config: ServerConfig, email: String?, newPassword: String): AdminPasswordResetResult {
			calls++
			this.email = email
			this.password = newPassword
			failure?.let { throw it }
			return result
		}
	}

	private fun command(resetter: AdminPasswordResetter) =
		ResetAdminPasswordCommand(resetter, configLoader = { ServerConfig() })

	@Test
	fun `piped stdin supplies the password and the email option picks the account`() {
		val resetter = FakeResetter()

		val result = command(resetter).test(listOf("--email", "admin@example.com"), stdin = "BrandNewPass123\n")

		assertEquals(0, result.statusCode)
		assertEquals(1, resetter.calls)
		assertEquals("admin@example.com", resetter.email)
		assertEquals("BrandNewPass123", resetter.password)
		assertContains(result.stdout, "admin@example.com")
	}

	@Test
	fun `without an email option none is passed`() {
		val resetter = FakeResetter()

		command(resetter).test(emptyList(), stdin = "BrandNewPass123")

		assertNull(resetter.email)
	}

	@Test
	fun `the server's dev flag is accepted`() {
		val resetter = FakeResetter()

		val result = command(resetter).test(listOf("--dev"), stdin = "BrandNewPass123")

		assertEquals(0, result.statusCode)
		assertEquals(1, resetter.calls)
	}

	@Test
	fun `the password never comes from argv`() {
		val resetter = FakeResetter()

		val result = command(resetter).test(listOf("--password", "BrandNewPass123"), stdin = "")

		assertEquals(1, result.statusCode)
		assertEquals(0, resetter.calls)
	}

	@Test
	fun `empty stdin is an error and nothing is reset`() {
		val resetter = FakeResetter()

		val result = command(resetter).test(emptyList(), stdin = "")

		assertEquals(1, result.statusCode)
		assertEquals(0, resetter.calls)
		assertContains(result.stderr, "password")
	}

	@Test
	fun `an interactive terminal asks twice and refuses a mismatch`() {
		val resetter = FakeResetter()

		val result = command(resetter).test(emptyList(), stdin = "first\nsecond", inputInteractive = true)

		assertEquals(1, result.statusCode)
		assertEquals(0, resetter.calls)
		assertContains(result.stderr, "match")
	}

	@Test
	fun `an interactive terminal accepts a confirmed password`() {
		val resetter = FakeResetter()

		val result = command(resetter).test(
			emptyList(),
			stdin = "BrandNewPass123\nBrandNewPass123",
			inputInteractive = true,
		)

		assertEquals(0, result.statusCode)
		assertEquals("BrandNewPass123", resetter.password)
		assertFalse(result.output.contains("BrandNewPass123"))
	}

	@Test
	fun `several admins without an email lists them and fails`() {
		val resetter = FakeResetter(
			AdminPasswordResetResult.MultipleAdmins(listOf("first@example.com", "second@example.com")),
		)

		val result = command(resetter).test(emptyList(), stdin = "BrandNewPass123")

		assertEquals(1, result.statusCode)
		assertContains(result.stderr, "--email")
		assertContains(result.stderr, "first@example.com")
		assertContains(result.stderr, "second@example.com")
	}

	@Test
	fun `a non-admin email fails`() {
		val resetter = FakeResetter(AdminPasswordResetResult.NotAdmin("writer@example.com"))

		val result = command(resetter).test(listOf("--email", "writer@example.com"), stdin = "BrandNewPass123")

		assertEquals(1, result.statusCode)
		assertContains(result.stderr, "writer@example.com")
		assertContains(result.stderr, "not an admin")
	}

	@Test
	fun `a rejected password reports the rule`() {
		val resetter = FakeResetter(AdminPasswordResetResult.PasswordRejected(PasswordValidationResult.TOO_SHORT))

		val result = command(resetter).test(emptyList(), stdin = "short")

		assertEquals(1, result.statusCode)
		assertContains(result.stderr, "8")
	}

	@Test
	fun `a database that cannot be opened points at the running server`() {
		val resetter = FakeResetter(failure = DatabaseUnavailableException(IllegalStateException("could not lock epg-lock")))

		val result = command(resetter).test(emptyList(), stdin = "BrandNewPass123")

		assertEquals(1, result.statusCode)
		assertContains(result.stderr, "stop")
		assertContains(result.stderr, "epg-lock")
	}
}
