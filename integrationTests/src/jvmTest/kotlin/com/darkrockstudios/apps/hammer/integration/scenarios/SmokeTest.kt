package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertTrue

/**
 * Smallest end-to-end check: client and server are both empty, sync succeeds,
 * and afterwards the server has a project record for this user. Validates the
 * full bootstrap (Koin, HTTP, auth, project creation, sync handshake) before
 * we layer on entity-level scenarios.
 */
class SmokeTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 60)
	fun `empty client and server sync without errors`() = runBlocking {
		val client = HeadlessClient.create(
			projectName = "smoke project",
			serverSettings = makeServerSettings(),
		)

		assertTrue(fileSystem.exists(client.projectPath), "Local project dir must exist before sync")

		val result = client.sync()

		assertTrue(result, "Sync should return true on success")
		assertTrue(fileSystem.exists(client.projectPath), "Local project dir must still exist after sync")

		// Server should now have a project record assigned to this user.
		val serverProjectCount = database().serverDatabase.projectQueries
			.getProjectsCount(userId)
			.executeAsOne()
		assertTrue(
			serverProjectCount == 1L,
			"Server should have exactly one project after sync, got $serverProjectCount"
		)

		client.close()
	}
}
