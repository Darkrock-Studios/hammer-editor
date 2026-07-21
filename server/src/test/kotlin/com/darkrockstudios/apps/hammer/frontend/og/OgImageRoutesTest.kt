package com.darkrockstudios.apps.hammer.frontend.og

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.project.access.PublicProjectResult
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gating tests for the ID-driven OG routes: a share card renders only for a subject the route can
 * publicly resolve (community author / publicly-published story); everything else 404s so the page
 * falls back to its static card rather than advertising a broken image.
 */
class OgImageRoutesTest {

	private val fileSystem = FakeFileSystem()
	private val cacheDir = "/cache/og".toPath()

	private fun fakeAccount(accountId: Long, penName: String?, isCommunity: Boolean): Account = mockk {
		every { id } returns accountId
		every { pen_name } returns penName
		every { community_member } returns isCommunity
	}

	private fun ApplicationTestBuilder.installRoutes(
		accounts: AccountsRepository,
		access: ProjectAccessRepository = mockk(),
	) {
		val service = OgImageService(OgImageRenderer(), fileSystem, cacheDir)
		application { routing { ogImageRoutes(accounts, access, service) } }
	}

	private fun ByteArray.isPng(): Boolean =
		size >= 8 && this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte() &&
			this[2] == 'N'.code.toByte() && this[3] == 'G'.code.toByte()

	@Test
	fun `community author renders a png card`() = testApplication {
		val accounts = mockk<AccountsRepository>()
		coEvery { accounts.getAccountOrNull(1L) } returns fakeAccount(1L, "Jane Doe", isCommunity = true)
		installRoutes(accounts)

		val response = client.get("/og/a/1.png")

		assertEquals(HttpStatusCode.OK, response.status)
		assertEquals(ContentType.Image.PNG, response.contentType()?.withoutParameters())
		assertTrue(response.body<ByteArray>().isPng(), "expected a PNG body")
	}

	@Test
	fun `non-community author 404s so the static fallback is used`() = testApplication {
		val accounts = mockk<AccountsRepository>()
		coEvery { accounts.getAccountOrNull(2L) } returns fakeAccount(2L, "Private Pat", isCommunity = false)
		installRoutes(accounts)

		assertEquals(HttpStatusCode.NotFound, client.get("/og/a/2.png").status)
	}

	@Test
	fun `unknown account id 404s`() = testApplication {
		val accounts = mockk<AccountsRepository>()
		coEvery { accounts.getAccountOrNull(99L) } returns null
		installRoutes(accounts)

		assertEquals(HttpStatusCode.NotFound, client.get("/og/a/99.png").status)
	}

	@Test
	fun `non-numeric account id 404s`() = testApplication {
		installRoutes(mockk())
		assertEquals(HttpStatusCode.NotFound, client.get("/og/a/not-a-number.png").status)
	}

	@Test
	fun `publicly published story renders a png card`() = testApplication {
		val uuid = "11111111-1111-1111-1111-111111111111"
		val access = mockk<ProjectAccessRepository>()
		coEvery { access.findPublicProjectByUuid(any()) } returns
			PublicProjectResult.Success(1L, ProjectId(uuid), "My Story", "Jane Doe", isPublic = true)
		installRoutes(accounts = mockk(), access = access)

		val response = client.get("/og/s/$uuid.png")

		assertEquals(HttpStatusCode.OK, response.status)
		assertEquals(ContentType.Image.PNG, response.contentType()?.withoutParameters())
		assertTrue(response.body<ByteArray>().isPng(), "expected a PNG body")
	}

	@Test
	fun `malformed story uuid 404s without touching the database`() = testApplication {
		val access = mockk<ProjectAccessRepository>()
		// The guard must short-circuit before any query — a malformed uuid would otherwise raise a
		// Postgres cast error (500). If the route calls through, this stub blows up the test.
		coEvery { access.findPublicProjectByUuid(any()) } throws AssertionError("should not query on a malformed uuid")
		installRoutes(accounts = mockk(), access = access)

		assertEquals(HttpStatusCode.NotFound, client.get("/og/s/not-a-uuid.png").status)
	}

	@Test
	fun `non-public story 404s so the static fallback is used`() = testApplication {
		val uuid = "22222222-2222-2222-2222-222222222222"
		val access = mockk<ProjectAccessRepository>()
		coEvery { access.findPublicProjectByUuid(any()) } returns PublicProjectResult.NotFound
		installRoutes(accounts = mockk(), access = access)

		assertEquals(HttpStatusCode.NotFound, client.get("/og/s/$uuid.png").status)
	}
}
