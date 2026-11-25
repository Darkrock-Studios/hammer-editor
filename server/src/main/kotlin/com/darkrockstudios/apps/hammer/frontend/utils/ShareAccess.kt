package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.frontend.data.ShareAccessSession
import com.darkrockstudios.apps.hammer.frontend.data.UnlockedShare
import com.darkrockstudios.apps.hammer.project.access.sharePasswordFingerprint
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * How long an unlocked private share stays open in a browser: a reader on a shared or borrowed
 * computer isn't left inside someone's private story for long. A share that ends sooner ends it.
 */
val SHARE_ACCESS_LIFETIME = 24.hours

/**
 * The most shares one browser keeps unlocked; the oldest goes first. Keeps the encrypted cookie
 * well inside the browsers' 4 KB limit.
 */
const val MAX_UNLOCKED_SHARES = 12

/**
 * This browser's unlocked shares that haven't lapsed, as share id to password fingerprint, in the
 * order they were unlocked (oldest first).
 */
fun ApplicationCall.unlockedShares(now: Instant): Map<Long, String> {
	val nowMillis = now.toEpochMilliseconds()
	return sessions.get<ShareAccessSession>()?.shares.orEmpty()
		.filter { it.expires > nowMillis }
		.associate { it.accessId to it.fingerprint }
}

/**
 * Remembers that this browser unlocked share [accessId] with [password], for
 * [SHARE_ACCESS_LIFETIME] or until [shareExpiresAt] if that is sooner. Lapsed entries are dropped
 * and an earlier entry for the same share is replaced.
 */
fun ApplicationCall.rememberUnlockedShare(
	accessId: Long,
	password: String,
	shareExpiresAt: Instant?,
	now: Instant,
) {
	val nowMillis = now.toEpochMilliseconds()
	val lifetimeEnd = now + SHARE_ACCESS_LIFETIME
	val expires = (if (shareExpiresAt != null && shareExpiresAt < lifetimeEnd) shareExpiresAt else lifetimeEnd)
		.toEpochMilliseconds()

	val kept = sessions.get<ShareAccessSession>()?.shares.orEmpty()
		.filter { it.accessId != accessId && it.expires > nowMillis }
	val entry = UnlockedShare(accessId = accessId, fingerprint = sharePasswordFingerprint(password), expires = expires)
	sessions.set(ShareAccessSession(shares = (kept + entry).takeLast(MAX_UNLOCKED_SHARES)))
}
