package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The upload-direction mirror of [ResyncStabilityMatrixTest]: when a client creates entities and
 * syncs them up, syncing again with nothing changed must be silent over the wire — nothing
 * re-uploaded, nothing pulled back. A client that uploads an entity it then can't reproduce the
 * server's hash for would re-upload (or fight a download) on every sync.
 *
 * Sweeps each entity type across its hashed fields. The image case is intentionally omitted: the
 * image hash is computed identically in both directions, and [ResyncStabilityMatrixTest] already
 * proves it round-trips, so an upload-side image case would add setup cost without new coverage.
 */
class UploadResyncStabilityMatrixTest : RoundTripTestBase() {

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	@Timeout(value = 120)
	fun `re-syncing locally-created entities is silent`(case: Case) = runBlocking {
		val client = newClient("upload matrix ${case.label}")

		case.create(client)

		val wire = tapWire()

		// First sync uploads the freshly-created entities.
		assertTrue(client.syncNoConflict(), "first sync should succeed")
		assertTrue(
			wire.entitiesUploaded().isNotEmpty(),
			"first sync should have uploaded the created entities",
		)

		wire.reset()

		// Second sync must be completely silent.
		assertTrue(client.syncNoConflict(), "second sync should succeed")
		assertEquals(emptyList(), wire.entitiesPulled(), "resync downloaded a locally-created entity")
		assertEquals(emptyList(), wire.entitiesUploaded(), "resync re-uploaded an unchanged entity")
	}

	class Case(val label: String, val create: suspend (HeadlessClient) -> Unit) {
		override fun toString() = label
	}

	companion object {
		private suspend fun newScene(client: HeadlessClient, name: String, content: String) =
			assertNotNull(client.sceneEditor.createScene(parent = null, sceneName = name)).also { scene ->
				client.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, content))
			}

		@JvmStatic
		fun cases(): List<Arguments> = listOf(
			Case("scene with content") { newScene(it, "Scene", "body text") },
			Case("scene with empty content") { newScene(it, "Scene", "") },
			Case("scene with outline, notes, and tags") { client ->
				val scene = newScene(client, "Scene", "body")
				val existing = client.sceneEditorService.loadSceneMetadata(scene.id)
				client.sceneEditorService.storeMetadata(
					existing.copy(outline = "an outline", notes = "some notes", tags = setOf("a", "b")),
					scene.id,
				)
			},
			Case("scene group") { client ->
				assertNotNull(client.sceneEditorService.createGroup(parent = null, groupName = "Group"))
			},
			Case("archived scene") { client ->
				val scene = newScene(client, "Doomed", "body")
				assertTrue(client.sceneEditorService.archiveScene(scene), "archive should succeed")
			},
			Case("note without tags") { client ->
				val notes: NotesRepository = client.scope.get()
				notes.createNote("note body")
			},
			Case("note with tags") { client ->
				val notes: NotesRepository = client.scope.get()
				notes.createNote("note body", tags = setOf("x", "y"))
			},
			Case("timeline event without date") { client ->
				val timeline: TimeLineRepository = client.scope.get()
				timeline.initialize()
				timeline.createEvent(content = "event", date = null)
			},
			Case("timeline event with date and tags") { client ->
				val timeline: TimeLineRepository = client.scope.get()
				timeline.initialize()
				timeline.createEvent(content = "event", date = "1920", tags = setOf("war"))
			},
			Case("encyclopedia entry, minimal") { client ->
				val encyclopedia: EncyclopediaRepository = client.scope.get()
				encyclopedia.ensureEntriesLoaded()
				encyclopedia.createEntry("Hero", EntryType.PERSON, "brave", emptySet(), imagePath = null)
			},
			Case("encyclopedia entry, tags and aliases") { client ->
				val encyclopedia: EncyclopediaRepository = client.scope.get()
				encyclopedia.ensureEntriesLoaded()
				encyclopedia.createEntry(
					name = "Hero",
					type = EntryType.PERSON,
					text = "brave",
					tags = setOf("legend"),
					imagePath = null,
					aliases = listOf("The Brave", "Champion"),
				)
			},
			Case("scene draft") { client ->
				val scene = newScene(client, "Scene", "body")
				assertNotNull(client.draftRepository.saveDraft(scene, "Draft One"))
			},
		).map { Arguments.of(it) }
	}
}
