package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.appmattus.crypto.Algorithm
import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeaHashItem
import korlibs.crypto.encoding.base64Url

/**
 * Account-wide hash over the live idea set — the ideas analogue of [ProjectContentHasher].
 * The server sends it in the begin-sync response; a client whose locked baselines produce the
 * same hash (and which has no pending local work) skips the ideas phase entirely, so an
 * unchanged ideas set costs zero extra round-trips.
 *
 * Deletion tombstones are deliberately excluded: a tombstone only matters to a client that
 * still holds the idea, and such a client's baseline set necessarily disagrees with the
 * server's live set, which already forces the phase to run.
 */
object IdeasStateHasher {
	fun hash(ideas: List<IdeaHashItem>): String {
		val buf = ByteArray(4)
		val d = Algorithm.MurmurHash3_X64_128().createDigest()
		// Sorted for a canonical order across devices; the size prefix keeps
		// adjacent (uuid, hash) strings from colliding across boundaries.
		d.update(ideas.size, buf)
		ideas.sortedBy { it.id.id }.forEach { item ->
			d.update(item.id.id, buf)
			d.update(item.hash, buf)
		}
		return d.digest().base64Url
	}
}
