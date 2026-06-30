package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.random.Random
import kotlin.test.assertTrue

/**
 * Property test for the mixed regime: a single client drives a random interleaving of creates, edits,
 * deletes, and renames across every entity type, syncing at random points. Because there's no other
 * device, no sync may ever conflict; and once the dust settles the client must be converged with the
 * server and an extra sync must be silent.
 *
 * This is the broad net the targeted matrices can't cast — it explores orderings no hand-written
 * scenario enumerates (a delete between two creates, a metadata edit then rename then sync, a rename
 * of an entity created earlier in the same run). A divergence bug like the null-timestamp re-download
 * surfaces here as a non-silent final resync. Seeded for replay; add seeds to widen coverage.
 */
class MixedSyncFuzzTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 300)
	fun `random multi-entity client op sequences stay convergent and silent`() = runBlocking {
		for (seed in SEEDS) {
			runSequence(seed)
		}
	}

	private suspend fun runSequence(seed: Long) {
		val rng = Random(seed)
		val client = newClient("mixed fuzz $seed")
		val notes: NotesRepository = client.scope.get()
		val timeline: TimeLineRepository = client.scope.get()
		val encyclopedia: EncyclopediaRepository = client.scope.get()
		timeline.initialize()
		encyclopedia.ensureEntriesLoaded()

		val scenes = mutableListOf<Int>()
		val noteState = mutableMapOf<Int, NoteContent>()
		val eventState = mutableMapOf<Int, TimeLineEvent>()
		val entries = mutableListOf<Int>()

		// Seed a baseline so early edit/delete ops have something to act on.
		newScene(client, scenes, "Opening", "the beginning")
		notes.createNote("baseline note").let { if (isSuccess(it)) noteState[it.data.id] = it.data }
		assertTrue(client.syncNoConflict(), "[seed $seed] baseline sync should succeed")

		repeat(ITERATIONS) { i ->
			val tag = "[seed $seed iter $i]"
			when (rng.nextInt(15)) {
				0 -> newScene(client, scenes, "Scene $i", "content $i")
				1 -> notes.createNote("note $i", tags = randomTags(rng))
					.let { if (isSuccess(it)) noteState[it.data.id] = it.data }
				2 -> timeline.createEvent(content = "event $i", date = if (rng.nextBoolean()) "$i" else null)
					.let { eventState[it.id] = it }
				3 -> encyclopedia.createEntry("Entry $i", EntryType.PERSON, "about $i", randomTags(rng), null)
					.instance?.entry?.id?.let { entries.add(it) }

				4 -> scenes.randomOrNull(rng)?.let { id ->
					client.sceneEditor.getSceneItemFromId(id)?.let { scene ->
						client.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, "edited $i"))
					}
				}
				5 -> scenes.randomOrNull(rng)?.let { id ->
					client.sceneEditor.getSceneItemFromId(id)?.let { scene ->
						client.sceneEditorService.renameScene(scene, "Renamed $i")
					}
				}
				6 -> scenes.randomOrNull(rng)?.let { id ->
					client.sceneEditor.getSceneItemFromId(id)?.let { scene ->
						val meta = client.sceneEditorService.loadSceneMetadata(id)
						client.sceneEditorService.storeMetadata(meta.copy(notes = "n$i", outline = "o$i"), id)
					}
				}

				7 -> noteState.keys.randomOrNull(rng)?.let { id ->
					val updated = noteState.getValue(id).copy(content = "note edit $i", tags = randomTags(rng))
					notes.updateNote(updated)
					noteState[id] = updated
				}
				8 -> eventState.keys.randomOrNull(rng)?.let { id ->
					val updated = eventState.getValue(id).copy(content = "event edit $i")
					timeline.updateEvent(updated)
					eventState[id] = updated
				}
				9 -> entries.randomOrNull(rng)?.let { id ->
					encyclopedia.updateEntry(encyclopedia.getEntryDef(id), "Entry $i", "about edit $i", randomTags(rng))
				}

				10 -> scenes.randomOrNull(rng)?.let { id ->
					client.sceneEditor.getSceneItemFromId(id)?.let { scene ->
						if (client.sceneEditorService.deleteScene(scene)) scenes.remove(id)
					}
				}
				11 -> noteState.keys.randomOrNull(rng)?.let { id ->
					notes.deleteNote(id)
					noteState.remove(id)
				}
				12 -> eventState.keys.randomOrNull(rng)?.let { id ->
					if (timeline.deleteEvent(eventState.getValue(id))) eventState.remove(id)
				}
				13 -> entries.randomOrNull(rng)?.let { id ->
					if (encyclopedia.deleteEntry(encyclopedia.getEntryDef(id))) entries.remove(id)
				}

				14 -> assertTrue(client.syncNoConflict(), "$tag sync should not conflict")
			}
		}

		assertTrue(client.syncNoConflict(), "[seed $seed] final sync should succeed")
		assertConverged(client.projectDef.name, client)
		assertResyncSilent(client)
	}

	private suspend fun newScene(client: HeadlessClient, scenes: MutableList<Int>, name: String, content: String) {
		val scene = client.sceneEditor.createScene(parent = null, sceneName = name) ?: return
		client.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, content))
		scenes.add(scene.id)
	}

	private fun randomTags(rng: Random): Set<String> =
		(0 until rng.nextInt(3)).map { "tag${rng.nextInt(4)}" }.toSet()

	private fun <T> List<T>.randomOrNull(rng: Random): T? = if (isEmpty()) null else this[rng.nextInt(size)]
	private fun <T> Set<T>.randomOrNull(rng: Random): T? =
		if (isEmpty()) null else elementAt(rng.nextInt(size))

	companion object {
		private const val ITERATIONS = 30
		private val SEEDS = listOf(1L, 777L, 20260630L)
	}
}
