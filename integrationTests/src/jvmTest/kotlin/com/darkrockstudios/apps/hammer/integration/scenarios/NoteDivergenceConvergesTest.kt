package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A note whose local copy diverges from the server's (e.g. another device edited it) must converge,
 * not ping-pong. Regression for the note-sync oscillation: the download wrote the server's copy to
 * disk but not the in-memory cache, so the heal re-uploaded the stale local copy and the two versions
 * swapped on the server every sync, forever. Surfaced on real projects as a handful of notes that
 * re-synced on every pass and never settled.
 */
class NoteDivergenceConvergesTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 120)
	fun `a note diverged from the server converges instead of oscillating`() = runBlocking {
		val project = "note divergence"
		val client = newClient(project)
		val notes: NotesRepository = client.scope.get()

		val noteId = notes.createNote("original content").let {
			assertTrue(isSuccess(it), "note creation should succeed"); it.data.id
		}
		assertTrue(client.syncNoConflict(), "initial upload sync should succeed")
		val pid = serverNumericProjectIdFor(project)!!

		// Another device edited this note: the server now holds a different version.
		mutateServerEntity(
			pid,
			ApiProjectEntity.NoteEntity(
				id = noteId,
				created = Instant.parse("2023-01-30T06:32:42.692656900Z"),
				content = "server-edited content",
				tags = emptySet(),
			),
		)

		// Adopt the server's version.
		assertTrue(client.syncNoConflict(), "reconciling sync should succeed")

		assertConverged(project, client)
		assertResyncSilent(client)
	}
}
