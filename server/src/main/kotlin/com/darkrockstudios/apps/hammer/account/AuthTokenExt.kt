package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.database.AuthToken
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

fun AuthToken.isExpired(clock: Clock): Boolean {
	return Instant.parse(expires) < clock.now()
}