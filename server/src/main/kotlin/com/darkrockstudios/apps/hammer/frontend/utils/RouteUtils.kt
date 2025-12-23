package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import io.ktor.server.sessions.*

fun CurrentSession.requireUser(): UserSession = get<UserSession>() ?: error("User session required but not found")