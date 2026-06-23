package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.database.AuthToken
import kotlin.time.Clock
import kotlin.time.Duration

fun AuthToken.isExpired(clock: Clock): Boolean = expires < clock.now()

/**
 * Whether the refresh credential has lapsed. The refresh token outlives the
 * access token by [refreshWindow]; because [expires] is pushed forward on every
 * token issue, this deadline slides with activity, so an actively-refreshed
 * session never lapses while an idle one expires [refreshWindow] after its
 * access token did.
 */
fun AuthToken.isRefreshExpired(clock: Clock, refreshWindow: Duration): Boolean =
	expires + refreshWindow < clock.now()
