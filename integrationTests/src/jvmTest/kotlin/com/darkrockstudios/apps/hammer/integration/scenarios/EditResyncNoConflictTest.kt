package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A single client, with no other device involved, edits a scene through the editor and syncs again.
 * That must not raise a conflict.
 *
 * The edit goes through the real editor path (`onContentChanged` + the debounce autosave), which
 * stamps `lastEdited`. Were the sync baseline re-derived from local state instead of locked to what
 * the server confirmed, that stamp would taint the baseline and the resync would conflict.
 */
class EditResyncNoConflictTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 60)
	fun `editing a scene through the editor and resyncing does not conflict`() = runBlocking {
		val client = HeadlessClient.create("edit resync", makeServerSettings())

		// Sync #1: establish the scene on the server.
		val scene = client.sceneEditor.createScene(parent = null, sceneName = "Scene")
		requireNotNull(scene)
		client.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, "first version"))
		assertTrue(client.sync(), "first sync should succeed")

		// Edit through the editor and let the debounce autosave fire (this stamps lastEdited).
		client.sceneEditorService.onContentChanged(
			SceneContent(scene, "second version, edited in the editor"),
			UpdateSource.Editor,
		)
		delay(1500)

		// Sync #2: single client, nothing else touched this scene — must not conflict.
		var conflicted = false
		val ok = client.sync(resolveConflict = { entity ->
			conflicted = true
			entity
		})
		assertTrue(ok, "second sync should succeed")
		assertFalse(
			conflicted,
			"A single client editing its own scene and resyncing raised a phantom conflict — " +
				"the sync baseline disagrees with what the server confirmed.",
		)

		client.close()
	}
}
