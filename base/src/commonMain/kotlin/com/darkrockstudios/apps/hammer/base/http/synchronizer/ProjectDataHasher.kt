package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.appmattus.crypto.Algorithm
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import korlibs.crypto.encoding.base64Url

/**
 * Field-presence is encoded with a leading `0`/`1` byte before each optional field so that
 * `theme = null` and `theme = ProjectTheme("", "")` produce distinct hashes — without the
 * presence byte, an empty-string theme would collide with the null case.
 */
object ProjectDataHasher {
	fun hash(data: ProjectData): String {
		val buf = ByteArray(4)
		val d = Algorithm.MurmurHash3_X64_128().createDigest()

		if (data.authorName != null) {
			d.update(1, buf)
			d.update(data.authorName, buf)
		} else {
			d.update(0, buf)
		}

		if (data.theme != null) {
			d.update(1, buf)
			d.update(data.theme.primary, buf)
			d.update(data.theme.secondary, buf)
		} else {
			d.update(0, buf)
		}

		if (data.wordCountGoal != null) {
			d.update(1, buf)
			d.update(data.wordCountGoal.cadence.ordinal, buf)
			d.update(data.wordCountGoal.count, buf)
		} else {
			d.update(0, buf)
		}

		// No presence byte, unlike the fields above: an empty set must contribute zero bytes so
		// tag-less data keeps its pre-tags hash (stored lastSyncedHash baselines and server rows
		// were computed without this field). Sorted for a canonical order across devices; the
		// size prefix keeps ["ab"] and ["a","b"] distinct.
		if (data.tags.isNotEmpty()) {
			d.update(data.tags.size, buf)
			data.tags.sorted().forEach { tag -> d.update(tag, buf) }
		}

		// Zero bytes when null, same rule as tags: pre-language hashes must stay identical.
		// The -1 marker can never be a tags size and the length prefix bounds the string,
		// so a language block cannot be read as a continuation of the tags block
		// (tags=["x"] and language="x" must not collide).
		if (data.language != null) {
			d.update(-1, buf)
			d.update(data.language.length, buf)
			d.update(data.language, buf)
		}

		return d.digest().base64Url
	}
}
