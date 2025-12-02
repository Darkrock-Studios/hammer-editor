package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.database.AuthToken
import kotlin.time.Clock
import kotlin.time.Instant

fun AuthToken.isExpired(clock: Clock): Boolean {
	return Instant.parse(expires) < clock.now()
}