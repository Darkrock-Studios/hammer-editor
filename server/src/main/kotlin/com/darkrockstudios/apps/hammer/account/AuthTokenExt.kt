package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.database.AuthToken
import kotlin.time.Clock

fun AuthToken.isExpired(clock: Clock): Boolean = expires < clock.now()
