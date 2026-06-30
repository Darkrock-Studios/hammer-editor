package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The invariant that the null-timestamp re-download bug violated: once a client has synced a set of
 * server-originated entities, syncing again with nothing changed must be **silent** — no entity body
 * pulled, nothing pushed back. A `200 download` or an `upload` on the second pass means the client's
 * stored copy hashes differently from the server's, so the entity ping-pongs forever.
 *
 * This sweeps every entity type across the field combinations most likely to round-trip lossily —
 * null/empty optional fields, populated optional fields, images, archive state — because those are
 * exactly the corners where a hashed field silently diverges. Watches the wire (via [tapWire]) so
 * the assertion is about real traffic, not what the client claims it did.
 */
class ResyncStabilityMatrixTest : RoundTripTestBase() {

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	@Timeout(value = 120)
	fun `re-syncing unchanged server entities is silent`(case: Case) = runBlocking {
		val project = "resync matrix ${case.label}"
		val client = newClient(project)

		// First sync registers the project on the server.
		assertTrue(client.sync(), "registration sync should succeed")
		val pid = serverNumericProjectIdFor(project)
		assertNotNull(pid, "server should have created the project")

		// Seed the case's entities as if another device had uploaded them.
		case.entities.forEach { seedServerEntity(pid, it) }
		val maxId = case.entities.maxOf { it.id }
		database().execute("UPDATE project SET last_id = $maxId WHERE id = $pid;")

		val wire = tapWire()

		// First real sync pulls everything down (and may heal impoverished server copies).
		assertTrue(client.syncNoConflict(), "download sync should succeed")
		assertEquals(
			case.entities.map { it.id }.toSet(),
			wire.entitiesPulled().toSet(),
			"first sync should have downloaded every seeded entity",
		)

		wire.reset()

		// Second sync must be completely silent: nothing changed, so nothing should move.
		assertTrue(client.syncNoConflict(), "second sync should succeed")
		assertEquals(emptyList(), wire.entitiesPulled(), "resync re-downloaded an unchanged entity")
		assertEquals(emptyList(), wire.entitiesUploaded(), "resync re-uploaded an unchanged entity")
	}

	data class Case(val label: String, val entities: List<ApiProjectEntity>) {
		override fun toString() = label
	}

	companion object {
		private val instant = Instant.fromEpochSeconds(1_700_000_000)

		private fun scene(
			id: Int,
			path: List<Int> = listOf(0),
			content: String = "body $id",
			sceneType: ApiSceneType = ApiSceneType.Scene,
			archived: Boolean = false,
			outline: String = "",
			notes: String = "",
			tags: Set<String> = emptySet(),
			confirmed: Set<Int> = emptySet(),
			dismissed: Set<Int> = emptySet(),
			created: Instant? = null,
			lastEdited: Instant? = null,
		) = ApiProjectEntity.SceneEntity(
			id = id,
			sceneType = sceneType,
			order = id - 1,
			name = "scene $id",
			path = path,
			content = content,
			outline = outline,
			notes = notes,
			archived = archived,
			confirmedReferences = confirmed,
			dismissedReferences = dismissed,
			tags = tags,
			created = created,
			lastEdited = lastEdited,
		)

		private fun one(label: String, entity: ApiProjectEntity) = Case(label, listOf(entity))

		@JvmStatic
		fun cases(): List<Arguments> = listOf(
			one("scene, null timestamps", scene(id = 1)),
			one("scene, populated timestamps", scene(id = 1, created = instant, lastEdited = instant)),
			one(
				"scene, optional text fields populated",
				scene(id = 1, outline = "an outline", notes = "some notes", tags = setOf("a", "b")),
			),
			one("scene, empty content", scene(id = 1, content = "")),
			one("scene group", scene(id = 1, sceneType = ApiSceneType.Group, content = "")),
			one("archived scene", scene(id = 1, path = emptyList(), archived = true)),
			one("note, no tags", ApiProjectEntity.NoteEntity(id = 1, content = "note body", created = instant)),
			one(
				"note, with tags",
				ApiProjectEntity.NoteEntity(id = 1, content = "note body", created = instant, tags = setOf("x", "y")),
			),
			one(
				"timeline event, null date",
				ApiProjectEntity.TimelineEventEntity(id = 1, order = 0, date = null, content = "event"),
			),
			one(
				"timeline event, with date and tags",
				ApiProjectEntity.TimelineEventEntity(
					id = 1, order = 0, date = "1920", content = "event", tags = setOf("war"),
				),
			),
			one(
				"encyclopedia entry, minimal",
				ApiProjectEntity.EncyclopediaEntryEntity(
					id = 1, name = "Hero", entryType = "person", text = "brave", tags = emptySet(),
					image = null, aliases = emptyList(),
				),
			),
			one(
				"encyclopedia entry, tags and aliases",
				ApiProjectEntity.EncyclopediaEntryEntity(
					id = 1, name = "Hero", entryType = "person", text = "brave",
					tags = setOf("legend"), image = null, aliases = listOf("The Brave", "Champion"),
				),
			),
			one(
				"encyclopedia entry, with image",
				ApiProjectEntity.EncyclopediaEntryEntity(
					id = 1, name = "Hero", entryType = "person", text = "brave", tags = emptySet(),
					// Canonical url-safe base64 of "img" so the client's re-encode matches.
					image = ApiProjectEntity.EncyclopediaEntryEntity.Image(base64 = "aW1n", fileExtension = "png"),
					aliases = emptyList(),
				),
			),
			Case(
				"scene draft references its scene",
				listOf(
					scene(id = 1, content = "draft target"),
					ApiProjectEntity.SceneDraftEntity(
						id = 2, sceneId = 1, created = instant, name = "Draft One", content = "draft body",
					),
				),
			),
		).map { Arguments.of(it) }
	}
}
