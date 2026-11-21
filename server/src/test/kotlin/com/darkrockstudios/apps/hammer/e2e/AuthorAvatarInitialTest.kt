package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.EncryptionConfig
import com.darkrockstudios.apps.hammer.EncryptionMode
import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

/**
 * The avatar circle on an author's page and on their community card shows the pen name's first
 * letter. The templates once read it as `{{penName.0}}`, which mustache.java resolves as a property
 * named "0" on a String, so the circle rendered empty.
 */
class AuthorAvatarInitialTest : EndToEndTest() {

	override val serverConfig = ServerConfig(
		encryption = EncryptionConfig(EncryptionMode.AES),
		communityEnabled = true,
	)

	private val userId = 1L

	private fun seedAuthor(penName: String, communityMember: Boolean = false) = runBlocking {
		E2eTestData.createAccount(TestAccount(email = "author@test.com", password = "password123!@#"), database())
		database().serverDatabase.accountQueries.updatePenName(pen_name = penName, id = userId)
		database().serverDatabase.accountQueries.updateCommunityMember(community_member = communityMember, id = userId)
	}

	@Test
	fun `the author page shows the pen name's initial in the avatar`(): Unit = runBlocking {
		doStartServer()
		seedAuthor("Lyren Vex")

		val body = client().get(route("a/Lyren-Vex")).bodyAsText()

		assertContains(body, """<span class="avatar-letter">L</span>""")
	}

	@Test
	fun `the community card shows the pen name's initial in the avatar`(): Unit = runBlocking {
		doStartServer()
		seedAuthor("Lyren Vex", communityMember = true)

		val body = client().get(route("community/authors")).bodyAsText()

		assertContains(body, """<span class="author-card__initial">L</span>""")
	}

	@Test
	fun `the initial is the capitalised first letter, accents included`(): Unit = runBlocking {
		doStartServer()
		seedAuthor("élodie")

		val body = client().get(route("a/%C3%A9lodie")).bodyAsText()

		assertContains(body, """<span class="avatar-letter">É</span>""")
	}
}
