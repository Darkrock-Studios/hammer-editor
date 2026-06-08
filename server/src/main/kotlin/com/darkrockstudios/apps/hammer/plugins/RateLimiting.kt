package com.darkrockstudios.apps.hammer.plugins

/** Name of the rate limiter applied to the login endpoint. */
const val LOGIN_RATE_LIMIT = "login"

/**
 * Configuration for the login rate limiter. Injected so it can be tuned per
 * environment — production uses a brute-force-blunting default, while tests
 * provide an effectively unlimited one so they never trip the limiter.
 *
 * [limit] requests are allowed per [refillPeriodSeconds] window, keyed by
 * source host.
 */
data class LoginRateLimitConfig(
	val limit: Int = 10,
	val refillPeriodSeconds: Long = 60,
)
