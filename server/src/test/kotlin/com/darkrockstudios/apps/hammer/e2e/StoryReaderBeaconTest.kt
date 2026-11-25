package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.e2e.util.TestProject
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.monitoring.ReaderKey
import com.darkrockstudios.apps.hammer.monitoring.StoryReaderCollector
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * Exercises the dwell-gated reader beacon (`POST /a/{penName}/{projectName}/read`).
 * story-reader.js fires this after the visitor dwells; here we drive the endpoint
 * directly and assert what actually gets recorded, since the response is always a
 * blank 204 by design (a bad beacon must look the same as a good one).
 */
class StoryReaderBeaconTest : EndToEndTest() {

	private val userId = 1L
	private val penName = "JaneAuthor"
	private val projectNameText = "Insurgency"
	private val projectUuid = Uuid.random()

	/** Reads (and clears) whatever the endpoint recorded into the live collector. */
	private fun recordedReads(): List<ReaderKey> =
		GlobalContext.get().get<StoryReaderCollector>().drainToKeys()

	private fun beaconPath(password: String? = null): String {
		val segment = ProjectName.projectSegment(projectNameText, projectUuid.toString())
		val query = if (password != null) "?p=$password" else ""
		return route("a/$penName/$segment/read$query")
	}

	private fun storyPath(password: String): String {
		val segment = ProjectName.projectSegment(projectNameText, projectUuid.toString())
		return route("a/$penName/$segment?p=$password")
	}

	/** Author account with a pen name and a project; access is left for each test to grant. */
	private fun seedAuthorAndProject() = runBlocking {
		E2eTestData.createAccount(TestAccount(email = "jane@test.com", password = "password123!@#"), database())
		database().serverDatabase.accountQueries.updatePenName(pen_name = penName, id = userId)
		E2eTestData.createProject(TestProject(name = projectNameText, uuid = projectUuid, userId = userId), database())
	}

	private fun projectRowId(): Long =
		database().serverDatabase.projectQueries.findProjectByName(userId, projectNameText).executeAsOne().id

	private fun grantAccess(password: String?) {
		database().serverDatabase.projectAccessQueries.insertAccess(
			project_id = projectRowId(),
			access_password = password,
			expires_at = null,
		)
	}

	@Test
	fun `a beacon for a published story records one reader`(): Unit = runBlocking {
		doStartServer()
		seedAuthorAndProject()
		grantAccess(password = null) // published (public) access

		val response = client().post(beaconPath())

		assertEquals(HttpStatusCode.NoContent, response.status)
		val reads = recordedReads()
		assertEquals(1, reads.size)
		assertEquals(projectRowId(), reads.single().projectId)
	}

	@Test
	fun `a beacon for a story with no public access records nothing`(): Unit = runBlocking {
		doStartServer()
		seedAuthorAndProject() // project exists but was never published or shared

		val response = client().post(beaconPath())

		assertEquals(HttpStatusCode.NoContent, response.status)
		assertEquals(0, recordedReads().size)
	}

	@Test
	fun `a private share only records once this browser has unlocked it`(): Unit = runBlocking {
		doStartServer()
		seedAuthorAndProject()
		grantAccess(password = "secret") // private share, no public access

		// Not unlocked: resolution stops at PasswordRequired, so nothing is recorded.
		val notUnlocked = client().post(beaconPath())
		assertEquals(HttpStatusCode.NoContent, notUnlocked.status)
		assertEquals(0, recordedReads().size)

		// A password in the beacon URL opens nothing: the beacon reads only the share cookie.
		val passwordInUrl = client().post(beaconPath(password = "secret"))
		assertEquals(HttpStatusCode.NoContent, passwordInUrl.status)
		assertEquals(0, recordedReads().size)

		// A browser that unlocked the share (here through an old ?p= link) is recorded.
		val browser = HttpClient { install(HttpCookies) }
		browser.get(storyPath(password = "secret"))
		val unlocked = browser.post(beaconPath())
		assertEquals(HttpStatusCode.NoContent, unlocked.status)
		assertEquals(1, recordedReads().size)
	}

	@Test
	fun `a beacon for an unknown pen name records nothing`(): Unit = runBlocking {
		doStartServer()
		seedAuthorAndProject()
		grantAccess(password = null)

		val response = client().post(route("a/NoSuchAuthor/${ProjectName.projectSegment(projectNameText, projectUuid.toString())}/read"))

		assertEquals(HttpStatusCode.NoContent, response.status)
		assertEquals(0, recordedReads().size)
	}
}
