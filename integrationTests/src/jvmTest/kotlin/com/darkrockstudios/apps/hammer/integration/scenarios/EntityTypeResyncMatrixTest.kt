package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The conflict-baseline logic lives in the shared sync layer, so it must hold for every entity type.
 * Each test creates an entity, syncs, edits it, and resyncs: a single client must never raise a
 * phantom conflict, and the server's stored hash must actually change across the edit (so a resync
 * that uploaded nothing can't pass vacuously).
 */
class EntityTypeResyncMatrixTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 60)
	fun `note edit and resync does not conflict`() = runBlocking {
		val project = "note matrix"
		val client = newClient(project)
		val notes: NotesRepository = client.scope.get()

		val created = notes.createNote("first body", tags = setOf("a"))
		check(isSuccess(created)) { "createNote failed: $created" }
		assertTrue(client.sync(), "first sync should succeed")
		val before = serverEntityHash(project, created.data.id)
		assertNotNull(before, "note should be on the server after the first sync")

		notes.updateNote(created.data.copy(content = "edited body", tags = setOf("b")))
		assertTrue(client.syncNoConflict(), "resync should succeed")
		assertNotEquals(before, serverEntityHash(project, created.data.id), "edit must reach the server")
	}

	@Test
	@Timeout(value = 60)
	fun `timeline event edit and resync does not conflict`() = runBlocking {
		val project = "timeline matrix"
		val client = newClient(project)
		val timeline: TimeLineRepository = client.scope.get()
		timeline.initialize()

		val event = timeline.createEvent(content = "battle", date = "1920", tags = setOf("a"))
		assertTrue(client.sync(), "first sync should succeed")
		val before = serverEntityHash(project, event.id)
		assertNotNull(before, "event should be on the server after the first sync")

		timeline.updateEvent(event.copy(content = "the great battle", date = "1921"))
		assertTrue(client.syncNoConflict(), "resync should succeed")
		assertNotEquals(before, serverEntityHash(project, event.id), "edit must reach the server")
	}

	@Test
	@Timeout(value = 60)
	fun `encyclopedia entry edit and resync does not conflict`() = runBlocking {
		val project = "encyclopedia matrix"
		val client = newClient(project)
		val encyclopedia: EncyclopediaRepository = client.scope.get()
		encyclopedia.ensureEntriesLoaded()

		val created = encyclopedia.createEntry(
			name = "Hero",
			type = EntryType.PERSON,
			text = "a brave hero",
			tags = setOf("hero"),
			imagePath = null,
		)
		val container = assertNotNull(created.instance, "entry should be created")
		assertTrue(client.sync(), "first sync should succeed")
		val before = serverEntityHash(project, container.entry.id)
		assertNotNull(before, "entry should be on the server after the first sync")

		val entryDef = encyclopedia.getEntryDef(container.entry.id)
		encyclopedia.updateEntry(
			oldEntryDef = entryDef,
			name = "Hero",
			text = "a very brave hero",
			tags = setOf("hero", "legend"),
			excludeFromDictionary = false,
		)
		assertTrue(client.syncNoConflict(), "resync should succeed")
		assertNotEquals(before, serverEntityHash(project, container.entry.id), "edit must reach the server")
	}

	@Test
	@Timeout(value = 60)
	fun `scene draft create and resync does not conflict`() = runBlocking {
		// Drafts are immutable (create-only); confirm one syncs and a later resync doesn't
		// re-flag it into a phantom conflict.
		val project = "draft matrix"
		val client = newClient(project)
		val scene = client.sceneEditor.createScene(parent = null, sceneName = "Scene")
		requireNotNull(scene)
		client.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, "body"))
		assertTrue(client.sync(), "scene sync should succeed")

		val drafts: SceneDraftRepository = client.scope.get()
		val draft = drafts.saveDraft(scene, "first pass")
		assertNotNull(draft, "draft should be created")
		assertTrue(client.sync(), "draft sync should succeed")
		assertNotNull(serverEntityHash(project, draft.id), "draft should be on the server")
		assertTrue(client.syncNoConflict(), "a later resync should not conflict")
	}

	@Test
	@Timeout(value = 60)
	fun `deleting a scene and resyncing removes it from the server without conflict`() = runBlocking {
		val project = "delete matrix"
		val client = newClient(project)
		val scene = client.sceneEditor.createScene(parent = null, sceneName = "Doomed")
		requireNotNull(scene)
		client.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, "body"))
		assertTrue(client.sync(), "first sync should succeed")
		assertNotNull(serverEntityHash(project, scene.id), "scene should exist on the server before deletion")

		assertTrue(client.sceneEditorService.deleteScene(scene), "local delete should succeed")
		assertTrue(client.syncNoConflict(), "delete resync should succeed")
		assertNull(serverEntityHash(project, scene.id), "server should have deleted the entity")
	}
}
