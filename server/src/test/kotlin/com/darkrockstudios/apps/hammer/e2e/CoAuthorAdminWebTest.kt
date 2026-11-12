package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.e2e.util.TestProject
import com.darkrockstudios.apps.hammer.e2e.util.WebEndToEndTest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** The admin user list counts the projects an account owns, never the ones it co-authors. */
class CoAuthorAdminWebTest : WebEndToEndTest() {

	private val adminEmail = "admin@test.com"
	private val ownerEmail = "owner@test.com"
	private val memberEmail = "member@test.com"
	private val password = "password123!@#"

	private fun accountId(email: String): Long =
		database().serverDatabase.accountQueries.findAccount(email).executeAsOne().id

	/** The project count printed in [email]'s row of the users fragment. */
	private fun projectCountIn(fragment: String, email: String): Int =
		Regex("""${Regex.escape(email)}[\s\S]*?Projects: (\d+)""").find(fragment)!!.groupValues[1].toInt()

	@Test
	fun `a co-authored project counts for its owner only`(): Unit = runBlocking {
		doStartServer()
		E2eTestData.createAccount(TestAccount(adminEmail, password, isAdmin = true), database())
		seedWhitelistedAccount(ownerEmail, password, penName = "Owner Pen")
		seedWhitelistedAccount(memberEmail, password, penName = "Member Pen")
		E2eTestData.createProject(
			TestProject(name = "Insurgency", uuid = Uuid.random(), userId = accountId(ownerEmail)),
			database(),
		)
		val projectRowId = database().serverDatabase.projectQueries
			.findProjectByName(accountId(ownerEmail), "Insurgency").executeAsOne().id
		database().serverDatabase.projectMemberQueries.insertMember(projectRowId, accountId(memberEmail))

		login(adminEmail, password).use { admin ->
			val fragment = admin.get(route("admin/users/user-fragment")) { header("HX-Request", "true") }.bodyAsText()
			assertEquals(1, projectCountIn(fragment, ownerEmail))
			assertEquals(0, projectCountIn(fragment, memberEmail))
		}
	}
}
