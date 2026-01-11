package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.http.API_ROUTE_PREFIX
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.inject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Plugin that redirects frontend requests to the setup page when no users exist in the database.
 * This allows admins to see instructions for creating their first account via the Hammer app.
 *
 * API routes are NOT blocked - they must remain accessible so the Hammer app can create the first account.
 */
val SetupModePlugin = createApplicationPlugin("SetupModePlugin") {
	val accountsRepository: AccountsRepository by application.inject()

	// Cache whether we have users to avoid DB queries on every request
	// Once we have users, we never go back to setup mode
	val hasUsersCache = AtomicBoolean(false)

	// Paths that should always be accessible, even in setup mode
	val allowedPaths = setOf(
		"/setup",
		"/locale",
	)

	// Path prefixes that should always be accessible
	val allowedPrefixes = listOf(
		"/$API_ROUTE_PREFIX/",  // All API routes (so Hammer app can create account)
		"/assets/",             // Static assets (CSS, JS, images)
	)

	on(CallSetup) { call ->
		// If we already know we have users, skip the check
		if (hasUsersCache.get()) {
			return@on
		}

		// Check if we have users (this will be cached after first successful account creation)
		val hasUsers = runBlocking { accountsRepository.hasUsers() }

		if (hasUsers) {
			// Update cache so we don't check again
			hasUsersCache.set(true)
			return@on
		}

		// No users exist - check if this request should be redirected
		val path = call.request.path()

		// Allow specific paths
		if (path in allowedPaths) {
			return@on
		}

		// Allow paths with specific prefixes
		for (prefix in allowedPrefixes) {
			if (path.startsWith(prefix)) {
				return@on
			}
		}

		// Redirect all other requests to the setup page
		call.respondRedirect("/setup")
	}
}
