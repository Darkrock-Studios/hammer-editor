package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.random.Random
import kotlin.test.assertTrue

/**
 * Property test: a single client driving a random interleaving of editor content edits, metadata
 * edits, renames, and syncs must NEVER raise a conflict — there is no other device, so any conflict
 * is a phantom. Content edits go through the real editor autosave path (which stamps lastEdited),
 * exercising orderings the hand-written scenarios don't cover (a rename between two content edits, a
 * metadata change between an edit and a sync).
 *
 * Seeded for reproducibility: a failure prints the seed and iteration so the exact sequence can be
 * replayed. Add seeds to [SEEDS] for more coverage.
 */
class SyncFuzzTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 180)
	fun `random single-client edit and sync sequences never conflict`() = runBlocking {
		for (seed in SEEDS) {
			runSequence(seed)
		}
	}

	private suspend fun runSequence(seed: Long) {
		val rng = Random(seed)
		val client = newClient("fuzz $seed")
		val scene = client.sceneEditor.createScene(parent = null, sceneName = "Fuzz Scene")
		requireNotNull(scene)
		client.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, "seed content"))
		assertTrue(client.sync(), "[seed $seed] initial sync should succeed")

		var contentVersion = 0
		repeat(ITERATIONS) { i ->
			// Re-resolve by id each step: a rename changes the on-disk filename, so a cached
			// SceneItem goes stale, but the id is stable.
			val current = client.sceneEditor.getSceneItemFromId(scene.id)
				?: error("[seed $seed] scene vanished at iteration $i")

			when (rng.nextInt(5)) {
				// Content edits are weighted 2/5 — they drive the lastEdited/autosave path.
				0, 1 -> {
					contentVersion++
					client.sceneEditorService.onContentChanged(
						SceneContent(current, "fuzz edit $contentVersion"),
						UpdateSource.Editor,
					)
					// Let the 500ms debounce autosave + the async lastEdited stamp fully land.
					delay(1500)
				}

				2 -> {
					val metadata = client.sceneEditorService.loadSceneMetadata(current.id)
					client.sceneEditorService.storeMetadata(
						metadata.copy(notes = "note at $i", outline = "outline at $i"),
						current.id,
					)
				}

				3 -> client.sceneEditorService.renameScene(current, "Fuzz Scene $i")

				4 -> assertTrue(client.syncNoConflict(), "[seed $seed] sync failed at iteration $i")
			}
		}

		// Flush whatever is still dirty; a single client must still not conflict.
		assertTrue(client.syncNoConflict(), "[seed $seed] final sync should succeed")
	}

	companion object {
		private const val ITERATIONS = 20
		private val SEEDS = listOf(1L, 8675309L, 424242L)
	}
}
