package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.TestProject
import com.darkrockstudios.apps.hammer.e2e.util.WebEndToEndTest
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.frontend.utils.SWAP_ERROR_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
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
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** The owner's Co-authors panel on the story page: invite, revoke and remove. */
class CoAuthorInviteWebTest : WebEndToEndTest() {

	private val ownerEmail = "owner@test.com"
	private val memberEmail = "member@test.com"
	private val strangerEmail = "stranger@test.com"
	private val password = "password123!@#"
	private val projectNameText = "Insurgency"
	private val projectUuid = Uuid.random()
	private val segment = ProjectName.projectSegment(projectNameText, projectUuid.toString())

	private fun accountId(email: String): Long =
		database().serverDatabase.accountQueries.findAccount(email).executeAsOne().id

	private fun projectRowId(): Long =
		database().serverDatabase.projectQueries.findProjectByName(accountId(ownerEmail), projectNameText)
			.executeAsOne().id

	private fun seed(ownerPenName: String? = "Owner Pen") = runBlocking {
		seedWhitelistedAccount(ownerEmail, password, penName = ownerPenName)
		seedWhitelistedAccount(memberEmail, password, penName = "Member Pen")
		seedWhitelistedAccount(strangerEmail, password)
		E2eTestData.createProject(
			TestProject(name = projectNameText, uuid = projectUuid, userId = accountId(ownerEmail)),
			database(),
		)
	}

	private fun addMember(email: String = memberEmail) {
		database().serverDatabase.projectMemberQueries.insertMember(projectRowId(), accountId(email))
	}

	private fun invitedEmails(): List<String> =
		database().serverDatabase.projectInviteQueries.getInvitesForProject(projectRowId()).executeAsList()
			.map { it.email }

	private fun memberIds(): List<Long> =
		database().serverDatabase.projectMemberQueries.getMembers(projectRowId()).executeAsList().map { it.user_id }

	private suspend fun HttpClient.invite(email: String): HttpResponse =
		post(route("story/$segment/coauthors/invites")) {
			header("HX-Request", "true")
			contentType(ContentType.Application.FormUrlEncoded)
			setBody("email=${URLEncoder.encode(email, "UTF-8")}")
		}

	private suspend fun HttpClient.hxDelete(path: String): HttpResponse =
		delete(route("story/$segment/coauthors/$path")) { header("HX-Request", "true") }

	@Test
	fun `the owner's story page lists co-authors and pending invites with controls`(): Unit = runBlocking {
		doStartServer()
		seed()
		addMember()
		database().serverDatabase.projectInviteQueries.insertInvite(projectRowId(), "pending@test.com", accountId(ownerEmail))

		login(ownerEmail, password).use { owner ->
			val body = owner.get(route("story/$segment")).bodyAsText()

			assertContains(body, """id="coauthor-panel"""")
			assertContains(body, "Member Pen")
			assertContains(body, "pending@test.com")
			assertContains(body, "/coauthors/members/${accountId(memberEmail)}")
			assertContains(body, "/coauthors/invites/")
			assertContains(body, "/coauthors/invite-dialog")
			assertContains(body, "2 of 5")
		}
	}

	@Test
	fun `an owner without a pen name is told why and cannot invite`(): Unit = runBlocking {
		doStartServer()
		seed(ownerPenName = null)

		login(ownerEmail, password).use { owner ->
			val page = owner.get(route("story/$segment")).bodyAsText()
			assertContains(page, "Set a pen name before inviting a co-author")
			assertFalse(page.contains("/coauthors/invite-dialog"))

			val response = owner.invite(memberEmail)
			assertEquals(HttpStatusCode.BadRequest, response.status)
			assertContains(response.bodyAsText(), "Set a pen name on your dashboard")
			assertEquals(emptyList(), invitedEmails())
		}
	}

	@Test
	fun `an invite answers the same whether or not an account holds the address`(): Unit = runBlocking {
		doStartServer()
		seed()

		login(ownerEmail, password).use { owner ->
			val known = owner.invite(" Member@Test.com ")
			val unknown = owner.invite("nobody@test.com")

			assertEquals(HttpStatusCode.OK, known.status)
			assertEquals(HttpStatusCode.OK, unknown.status)
			val knownBody = known.bodyAsText()
			val unknownBody = unknown.bodyAsText()
			assertContains(knownBody, "Invitation added for member@test.com")
			assertContains(unknownBody, "Invitation added for nobody@test.com")
			assertContains(unknownBody, "member@test.com")
			assertContains(unknownBody, "nobody@test.com")
			assertContains(knownBody, """<div id="coauthor-dialog-container" hx-swap-oob="innerHTML"></div>""")
			assertEquals(listOf("member@test.com", "nobody@test.com"), invitedEmails())
		}
	}

	@Test
	fun `re-inviting a pending address adds no second row`(): Unit = runBlocking {
		doStartServer()
		seed()

		login(ownerEmail, password).use { owner ->
			assertEquals(HttpStatusCode.OK, owner.invite("pat@test.com").status)
			assertEquals(HttpStatusCode.OK, owner.invite("pat@test.com").status)
			assertEquals(listOf("pat@test.com"), invitedEmails())
		}
	}

	@Test
	fun `refusals render inline in the invite form`(): Unit = runBlocking {
		doStartServer()
		seed()
		addMember()

		login(ownerEmail, password).use { owner ->
			val own = owner.invite(ownerEmail)
			assertEquals(HttpStatusCode.BadRequest, own.status)
			assertEquals("true", own.headers[SWAP_ERROR_HEADER])
			assertEquals("#coauthor-invite-form", own.headers["HX-Retarget"])
			val ownBody = own.bodyAsText()
			assertContains(ownBody, "your own address")
			assertContains(ownBody, """id="coauthor-invite-form"""")
			assertFalse(ownBody.contains("toast"))

			val duplicate = owner.invite(memberEmail)
			assertEquals(HttpStatusCode.BadRequest, duplicate.status)
			assertContains(duplicate.bodyAsText(), "already a co-author")

			val invalid = owner.invite("not-an-address")
			assertEquals(HttpStatusCode.BadRequest, invalid.status)
			assertContains(invalid.bodyAsText(), "value=\"not-an-address\"")

			assertEquals(emptyList(), invitedEmails())
		}
	}

	@Test
	fun `members plus pending invites are capped at five`(): Unit = runBlocking {
		doStartServer()
		seed()
		addMember()

		login(ownerEmail, password).use { owner ->
			(1..4).forEach { assertEquals(HttpStatusCode.OK, owner.invite("writer$it@test.com").status) }

			val sixth = owner.invite("writer5@test.com")
			assertEquals(HttpStatusCode.BadRequest, sixth.status)
			assertContains(sixth.bodyAsText(), "5 co-authors and pending invitations at most")
			assertEquals(4, invitedEmails().size)

			val page = owner.get(route("story/$segment")).bodyAsText()
			assertFalse(page.contains("/coauthors/invite-dialog"))
			assertContains(page, "5 of 5")
		}
	}

	@Test
	fun `a co-author is refused and a stranger finds nothing`(): Unit = runBlocking {
		doStartServer()
		seed()
		addMember()

		login(memberEmail, password).use { member ->
			assertEquals(HttpStatusCode.Forbidden, member.invite("pat@test.com").status)
			assertEquals(HttpStatusCode.Forbidden, member.hxDelete("members/${accountId(memberEmail)}").status)
			val dialog = member.get(route("story/$segment/coauthors/invite-dialog")) { header("HX-Request", "true") }
			assertEquals(HttpStatusCode.Forbidden, dialog.status)
		}
		login(strangerEmail, password).use { stranger ->
			assertEquals(HttpStatusCode.NotFound, stranger.invite("pat@test.com").status)
		}

		assertEquals(emptyList(), invitedEmails())
		assertEquals(listOf(accountId(memberEmail)), memberIds())
	}

	@Test
	fun `the owner revokes an invite and removes a member`(): Unit = runBlocking {
		doStartServer()
		seed()
		addMember()

		login(ownerEmail, password).use { owner ->
			owner.invite("pat@test.com")
			val inviteId = database().serverDatabase.projectInviteQueries.getInvitesForProject(projectRowId())
				.executeAsOne().id

			val revoked = owner.hxDelete("invites/$inviteId")
			assertEquals(HttpStatusCode.OK, revoked.status)
			assertFalse(revoked.bodyAsText().contains("pat@test.com"))
			assertEquals(emptyList(), invitedEmails())

			val removed = owner.hxDelete("members/${accountId(memberEmail)}")
			assertEquals(HttpStatusCode.OK, removed.status)
			assertFalse(removed.bodyAsText().contains("Member Pen"))
			assertTrue(memberIds().isEmpty())
		}
	}
}
