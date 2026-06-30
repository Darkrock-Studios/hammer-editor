package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A clean client should download server entities once, then leave them alone. After the first sync
 * pulls everything down, a second sync over an unchanged project must not pull a single entity body
 * back over the wire — every entity is already current, so the server should answer `304 Not
 * Modified` for each. A `200` on the second pass means the client's recomputed hash diverged from
 * what the server stored, so it re-downloads the same entity forever.
 *
 * Watches the actual HTTP traffic (via [tapWire]) rather than client logs, so the assertion is about
 * what crossed the wire, not what we think the client decided.
 */
class ResyncDownloadsNothingTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 120)
	fun `second sync of unchanged server entities pulls nothing over the wire`() = runBlocking {
		val project = "resync downloads nothing"
		val client = newClient(project)

		// First sync just registers the project on the server (no entities yet).
		assertTrue(client.sync(), "registration sync should succeed")
		val pid = serverNumericProjectIdFor(project)
		assertNotNull(pid, "server should have created the project")

		// Seed one entity of every type, as if another device had uploaded them.
		val seeded: List<ApiProjectEntity> = listOf(
			E2eTestData.createTestScene(id = 1),
			E2eTestData.createTestNote(id = 2),
			ApiProjectEntity.TimelineEventEntity(
				id = 3,
				order = 0,
				date = "1920",
				content = "a battle",
			),
			E2eTestData.createTestEncyclopediaEntry(id = 4),
			ApiProjectEntity.SceneDraftEntity(
				id = 5,
				sceneId = 1,
				created = Instant.fromEpochSeconds(1_700_000_000),
				name = "Draft One",
				content = "draft body",
			),
		)
		seeded.forEach { seedServerEntity(pid, it) }
		database().execute("UPDATE project SET last_id = 5 WHERE id = $pid;")

		// The scene was seeded with null timestamps; remember its hash so we can prove the heal.
		val seededSceneHash = (seeded.first() as ApiProjectEntity.SceneEntity).hash()

		val wire = tapWire()

		// First real sync: every seeded entity should come down.
		assertTrue(client.syncNoConflict(), "download sync should succeed")
		assertEquals(
			setOf(1, 2, 3, 4, 5),
			wire.entitiesPulled().toSet(),
			"first sync should have downloaded every server entity",
		)

		// The client backfilled the scene's null timestamps, so it should have healed the server's
		// copy in the same sync rather than just adopting the impoverished version.
		assertNotEquals(
			seededSceneHash,
			serverEntityHash(project, 1),
			"server's scene should have been healed with the backfilled timestamps",
		)

		wire.reset()

		// Second sync: nothing changed, so nothing should be pulled back down.
		assertTrue(client.syncNoConflict(), "second sync should succeed")
		assertEquals(
			emptyList(),
			wire.entitiesPulled(),
			"second sync re-downloaded entities that were already current locally",
		)
	}
}
