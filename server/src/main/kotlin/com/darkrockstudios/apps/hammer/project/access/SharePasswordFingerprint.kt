package com.darkrockstudios.apps.hammer.project.access

import java.security.MessageDigest
import java.util.Base64

/**
 * Identifies a share's current password without holding it: SHA-256, URL-safe Base64. A reader's
 * unlocked share carries this, so changing the share's password stops that entry matching. It
 * only ever travels inside the encrypted, signed share-access cookie.
 */
fun sharePasswordFingerprint(password: String): String =
	Base64.getUrlEncoder().withoutPadding().encodeToString(
		MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
	)

/** Constant-time comparison of two fingerprints. */
internal fun fingerprintsMatch(a: String, b: String): Boolean =
	MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
