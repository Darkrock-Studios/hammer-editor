package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.TestProject
import com.darkrockstudios.apps.hammer.e2e.util.WebEndToEndTest
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

/** What a co-author sees and can do on the owner's story pages. */
class CoAuthorStoryPageWebTest : WebEndToEndTest() {

	private val ownerEmail = "owner@test.com"
	private val memberEmail = "member@test.com"
	private val strangerEmail = "stranger@test.com"
	private val password = "password123!@#"
	private val ownerPenName = "OwnerPen"
	private val projectNameText = "Insurgency"
	private val projectUuid = Uuid.random()
	private val segment = ProjectName.projectSegment(projectNameText, projectUuid.toString())

	private fun accountId(email: String): Long =
		database().serverDatabase.accountQueries.findAccount(email).executeAsOne().id

	private fun projectRowId(): Long =
		database().serverDatabase.projectQueries.findProjectByName(accountId(ownerEmail), projectNameText)
			.executeAsOne().id

	private fun isMember(): Boolean =
		database().serverDatabase.projectMemberQueries.isMember(projectRowId(), accountId(memberEmail)).executeAsOne()

	private fun seed() = runBlocking {
		seedWhitelistedAccount(ownerEmail, password, penName = ownerPenName)
		seedWhitelistedAccount(memberEmail, password, penName = "Member Pen")
		seedWhitelistedAccount(strangerEmail, password)
		E2eTestData.createProject(
			TestProject(name = projectNameText, uuid = projectUuid, userId = accountId(ownerEmail)),
			database(),
		)
		E2eTestData.insertEntity(
			userId = accountId(ownerEmail),
			projectId = projectRowId(),
			entity = ApiProjectEntity.SceneEntity(
				id = 1,
				sceneType = ApiSceneType.Scene,
				order = 0,
				name = "Arrival",
				path = listOf(0),
				content = "The train came in late.",
			),
			testDatabase = database(),
			contentEncryptor = encryptor(),
		)
		database().serverDatabase.projectMemberQueries.insertMember(projectRowId(), accountId(memberEmail))
	}

	private suspend fun HttpClient.hxGet(path: String): HttpResponse =
		get(route(path)) { header("HX-Request", "true") }

	private suspend fun HttpClient.hxPost(path: String, body: String = ""): HttpResponse =
		post(route(path)) {
			header("HX-Request", "true")
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(body)
		}

	@Test
	fun `a co-author's story page shows the story and a way to leave, and no owner controls`(): Unit = runBlocking {
		doStartServer()
		seed()

		login(memberEmail, password).use { member ->
			val response = member.get(route("story/$segment"))
			assertEquals(HttpStatusCode.OK, response.status)
			val body = response.bodyAsText()

			assertContains(body, projectNameText)
			assertContains(body, "Shared by $ownerPenName")
			assertContains(body, "story-hero__read")
			assertContains(body, "/story/$segment/leave-dialog")
			assertContains(body, "story-stat--sync")
			for (ownerOnly in listOf(
				"publish-section", "review-panel", "coauthor-panel", "share-dialog", "kudos-panel", "/coauthors/",
			)) {
				assertFalse(body.contains(ownerOnly))
			}
		}
	}

	@Test
	fun `the owner's page keeps its controls and offers no leave`(): Unit = runBlocking {
		doStartServer()
		seed()

		login(ownerEmail, password).use { owner ->
			val body = owner.get(route("story/$segment")).bodyAsText()
			assertContains(body, "publish-section")
			assertContains(body, "coauthor-panel")
			assertFalse(body.contains("leave-dialog"))
			assertFalse(body.contains("Shared by"))

			assertEquals(HttpStatusCode.Forbidden, owner.hxGet("story/$segment/leave-dialog").status)
			assertEquals(HttpStatusCode.Forbidden, owner.hxPost("story/$segment/leave").status)
		}
	}

	@Test
	fun `a non-member finds nothing`(): Unit = runBlocking {
		doStartServer()
		seed()

		login(strangerEmail, password).use { stranger ->
			assertEquals(HttpStatusCode.NotFound, stranger.get(route("story/$segment")).status)
			assertEquals(HttpStatusCode.NotFound, stranger.get(route("story/$segment/read")).status)
			assertEquals(HttpStatusCode.NotFound, stranger.hxPost("story/$segment/leave").status)
		}
	}

	@Test
	fun `owner-only routes stay closed to a co-author`(): Unit = runBlocking {
		doStartServer()
		seed()

		login(memberEmail, password).use { member ->
			assertEquals(HttpStatusCode.NotFound, member.hxPost("story/$segment/publish").status)
			assertEquals(HttpStatusCode.NotFound, member.hxGet("story/$segment/share-dialog").status)
			assertEquals(HttpStatusCode.NotFound, member.hxGet("story/$segment/reviews/dialog").status)
		}
		assertEquals(
			0L,
			database().serverDatabase.projectAccessQueries.getAllAccessForProject(projectRowId()).executeAsList().size.toLong(),
		)
	}

	@Test
	fun `a co-author reads the owner's story`(): Unit = runBlocking {
		doStartServer()
		seed()

		login(memberEmail, password).use { member ->
			val response = member.get(route("story/$segment/read"))
			assertEquals(HttpStatusCode.OK, response.status)
			assertContains(response.bodyAsText(), "The train came in late.")
		}
	}

	@Test
	fun `a co-author leaves after confirming, and the story is gone from their pages`(): Unit = runBlocking {
		doStartServer()
		seed()

		login(memberEmail, password).use { member ->
			val dialog = member.hxGet("story/$segment/leave-dialog")
			assertEquals(HttpStatusCode.OK, dialog.status)
			val dialogBody = dialog.bodyAsText()
			assertContains(dialogBody, "stays with $ownerPenName, untouched")
			assertContains(dialogBody, """hx-post="/story/$segment/leave"""")

			val left = member.hxPost("story/$segment/leave")
			assertEquals(HttpStatusCode.NoContent, left.status)
			assertEquals("/dashboard", left.headers["HX-Redirect"])
			assertFalse(isMember())

			assertEquals(HttpStatusCode.NotFound, member.get(route("story/$segment")).status)
			assertFalse(member.get(route("dashboard")).bodyAsText().contains(segment))
		}
	}

	@Test
	fun `a co-author cannot give the story kudos`(): Unit = runBlocking {
		doStartServer()
		seed()
		database().serverDatabase.projectAccessQueries.insertAccess(
			project_id = projectRowId(),
			access_password = null,
			expires_at = null,
		)
		val kudosPath = "a/$ownerPenName/$segment/kudos"

		login(memberEmail, password).use { member ->
			val fragment = member.hxGet(kudosPath).bodyAsText()
			assertFalse(fragment.contains("story-kudos__form"))

			val response = member.hxPost(kudosPath, "kind=prose")
			assertEquals(HttpStatusCode.Forbidden, response.status)
			assertEquals(
				emptyList(),
				database().serverDatabase.storyKudosQueries.kindsForUser(projectRowId(), accountId(memberEmail))
					.executeAsList(),
			)
		}
	}
}
