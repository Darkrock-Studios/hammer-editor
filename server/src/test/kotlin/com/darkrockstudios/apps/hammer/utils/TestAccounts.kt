package com.darkrockstudios.apps.hammer.utils

import com.darkrockstudios.apps.hammer.Account
import kotlin.time.Clock
import kotlin.time.Instant

/** A plain account row for tests that need the auth gates to see a real account. */
fun testAccount(
	userId: Long = 0L,
	email: String = "test@test.com",
	isAdmin: Boolean = false,
	deletedAt: Instant? = null,
) = Account(
	id = userId,
	email = email,
	pen_name = null,
	password_hash = "hash",
	cipher_secret = "secret",
	created = Clock.System.now(),
	is_admin = isAdmin,
	last_sync = Clock.System.now(),
	bio = null,
	email_verified = true,
	community_member = false,
	deleted_at = deletedAt,
)
