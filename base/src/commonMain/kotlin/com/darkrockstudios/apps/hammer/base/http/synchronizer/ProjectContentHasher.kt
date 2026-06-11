package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.appmattus.crypto.Algorithm
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import korlibs.crypto.encoding.base64Url

/**
 * Aggregates a project's per-entity hashes plus its project-data hash into a single
 * project-wide content hash. Computed with identical logic on the client and the server
 * so the two can be compared directly by the pre-sync change probe (see SYNCING-PROTOCOL.md).
 *
 * Entities are folded in ascending-id order so the result is independent of enumeration
 * order. Writing activity is intentionally excluded: it is per-device and conflict-free, so
 * no single device ever holds the full union and a symmetric hash including it would never
 * match across devices.
 */
object ProjectContentHasher {
	/**
	 * Bump whenever the hashing scheme changes (here, in [EntityHasher], or in [ProjectDataHasher]).
	 * A cached client hash carrying an older version is treated as absent, forcing a normal sync
	 * that recomputes and re-caches it at the current version.
	 */
	const val ALGO_VERSION: Int = 1

	fun hash(entityHashes: Collection<EntityHash>, projectDataHash: String): String {
		val buf = ByteArray(4)
		val d = Algorithm.MurmurHash3_X64_128().createDigest()
		entityHashes.sortedBy { it.id }.forEach { entity ->
			d.update(entity.id, buf)
			d.update(entity.hash, buf)
		}
		d.update(projectDataHash, buf)
		return d.digest().base64Url
	}
}
