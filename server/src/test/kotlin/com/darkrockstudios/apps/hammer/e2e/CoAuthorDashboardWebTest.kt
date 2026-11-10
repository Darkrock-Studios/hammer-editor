package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.TestProject
import com.darkrockstudios.apps.hammer.e2e.util.WebEndToEndTest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Invitations and shared stories on the invitee's dashboard. */
class CoAuthorDashboardWebTest : WebEndToEndTest() {

	private val ownerEmail = "owner@test.com"
	private val inviteeEmail = "invitee@test.com"
	private val password = "password123!@#"
	private val projectNameText = "Insurgency"
	private val projectUuid = Uuid.random()

	private fun accountId(email: String): Long =
		database().serverDatabase.accountQueries.findAccount(email).executeAsOne().id

	private fun projectRowId(): Long =
		database().serverDatabase.projectQueries.findProjectByName(accountId(ownerEmail), projectNameText)
			.executeAsOne().id

	private fun seed(inviteePenName: String? = "Invitee Pen") = runBlocking {
		seedWhitelistedAccount(ownerEmail, password, penName = "Owner Pen")
		seedWhitelistedAccount(inviteeEmail, password, penName = inviteePenName)
		E2eTestData.createProject(
			TestProject(name = projectNameText, uuid = projectUuid, userId = accountId(ownerEmail)),
			database(),
		)
		database().serverDatabase.projectInviteQueries.insertInvite(projectRowId(), inviteeEmail, accountId(ownerEmail))
	}

	private fun inviteId(): Long =
		database().serverDatabase.projectInviteQueries.getInvitesForProject(projectRowId()).executeAsOne().id

	private fun isMember(): Boolean =
		database().serverDatabase.projectMemberQueries.isMember(projectRowId(), accountId(inviteeEmail)).executeAsOne()

	private suspend fun HttpClient.hxPost(path: String, body: String = ""): HttpResponse =
		post(route(path)) {
			header("HX-Request", "true")
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(body)
		}

	@Test
	fun `the dashboard shows an invite with accept and decline`(): Unit = runBlocking {
		doStartServer()
		seed()
		val id = inviteId()

		login(inviteeEmail, password).use { invitee ->
			val body = invitee.get(route("dashboard")).bodyAsText()
			assertContains(body, "Invitations")
			assertContains(body, projectNameText)
			assertContains(body, "From Owner Pen")
			assertContains(body, "/dashboard/invites/$id/accept")
			assertContains(body, "/dashboard/invites/$id/decline")
			assertFalse(body.contains("""name="penName" class="form-input" required"""))
		}
	}

	@Test
	fun `an invite from an inactive owner is hidden and cannot be accepted`(): Unit = runBlocking {
		doStartServer()
		seed()
		val id = inviteId()
		database().serverDatabase.accountQueries.markDeleted(Clock.System.now(), accountId(ownerEmail))

		login(inviteeEmail, password).use { invitee ->
			val body = invitee.get(route("dashboard")).bodyAsText()
			assertFalse(body.contains("/dashboard/invites/$id/accept"))

			assertEquals(HttpStatusCode.NotFound, invitee.hxPost("dashboard/invites/$id/accept").status)
			assertFalse(isMember())
		}
	}

	@Test
	fun `accepting makes the account a member and lists the story as shared`(): Unit = runBlocking {
		doStartServer()
		seed()
		val id = inviteId()

		login(inviteeEmail, password).use { invitee ->
			val response = invitee.hxPost("dashboard/invites/$id/accept")
			assertEquals(HttpStatusCode.OK, response.status)
			val body = response.bodyAsText()
			assertContains(body, "You are now a co-author of $projectNameText")
			assertContains(body, """<div id="shared-projects" hx-swap-oob="true">""")
			assertContains(body, "Shared by Owner Pen")
			assertTrue(isMember())

			val dashboard = invitee.get(route("dashboard")).bodyAsText()
			assertContains(dashboard, "Shared with you")
			assertContains(dashboard, "card--shared")
			assertFalse(dashboard.contains("configure server synchronization"))
			assertFalse(dashboard.contains("/dashboard/invites/$id/accept"))
		}
	}

	@Test
	fun `accepting without a pen name asks for one inline, then sets it and joins`(): Unit = runBlocking {
		doStartServer()
		seed(inviteePenName = null)
		val id = inviteId()

		login(inviteeEmail, password).use { invitee ->
			val dashboard = invitee.get(route("dashboard")).bodyAsText()
			assertContains(dashboard, "invite-penname-$id")
			assertContains(dashboard, "Set pen name and accept")

			val missing = invitee.hxPost("dashboard/invites/$id/accept", "penName=")
			assertEquals(HttpStatusCode.BadRequest, missing.status)
			assertContains(missing.bodyAsText(), "invite-error-$id")
			assertFalse(isMember())

			val accepted = invitee.hxPost(
				"dashboard/invites/$id/accept",
				"penName=${URLEncoder.encode("New Pen", "UTF-8")}",
			)
			assertEquals(HttpStatusCode.NoContent, accepted.status)
			assertEquals("true", accepted.headers["HX-Refresh"])
			assertTrue(isMember())
			assertEquals(
				"New Pen",
				database().serverDatabase.accountQueries.findAccount(inviteeEmail).executeAsOne().pen_name,
			)
		}
	}

	@Test
	fun `a taken pen name keeps the invite open with the reason`(): Unit = runBlocking {
		doStartServer()
		seed(inviteePenName = null)
		val id = inviteId()

		login(inviteeEmail, password).use { invitee ->
			val response = invitee.hxPost(
				"dashboard/invites/$id/accept",
				"penName=${URLEncoder.encode("Owner Pen", "UTF-8")}",
			)
			assertEquals(HttpStatusCode.BadRequest, response.status)
			val body = response.bodyAsText()
			assertContains(body, "invite-error-$id")
			assertContains(body, """value="Owner Pen"""")
			assertFalse(isMember())
			assertNull(database().serverDatabase.accountQueries.findAccount(inviteeEmail).executeAsOne().pen_name)
		}
	}

	@Test
	fun `declining deletes the invite`(): Unit = runBlocking {
		doStartServer()
		seed()
		val id = inviteId()

		login(inviteeEmail, password).use { invitee ->
			val response = invitee.hxPost("dashboard/invites/$id/decline")
			assertEquals(HttpStatusCode.OK, response.status)
			assertContains(response.bodyAsText(), "Invitation declined")
			assertEquals(
				emptyList(),
				database().serverDatabase.projectInviteQueries.getInvitesForProject(projectRowId()).executeAsList(),
			)
			assertFalse(isMember())
		}
	}

	@Test
	fun `another account cannot accept someone else's invite`(): Unit = runBlocking {
		doStartServer()
		seed()
		val id = inviteId()
		seedWhitelistedAccount("other@test.com", password, penName = "Other Pen")

		login("other@test.com", password).use { other ->
			assertEquals(HttpStatusCode.NotFound, other.hxPost("dashboard/invites/$id/accept").status)
			other.hxPost("dashboard/invites/$id/decline")
		}
		assertEquals(id, inviteId())
		assertFalse(isMember())
	}
}
