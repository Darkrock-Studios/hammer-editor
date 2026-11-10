package com.darkrockstudios.apps.hammer.health

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.AuthScheme
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory

/**
 * Health checks for uptime monitors. Mounted under the API prefix, so the full
 * paths are [HEALTH_ROUTE], `/database` and `/jobs/{slug}` beneath it.
 *
 * Without a token only the overall summary is available. A bearer token issued
 * from the admin Jobs page unlocks the full report and the per-check paths,
 * whose status code reflects that one check alone.
 */
fun Route.healthRoutes() {
	val healthService: HealthService = get()

	route("/health") {
		get {
			call.noStore()
			when (call.caller(healthService)) {
				Caller.ANONYMOUS -> {
					val summary = healthService.report().summary()
					call.respond(summary.ok.toStatus(), summary)
				}

				Caller.AUTHORIZED -> call.respond(healthService.report())
				Caller.REJECTED -> call.respond(HttpStatusCode.Unauthorized)
				Caller.UNVERIFIABLE -> call.respond(HttpStatusCode.ServiceUnavailable)
			}
		}

		get("/database") {
			call.noStore()
			if (!call.requireAuthorized(healthService)) return@get
			val database = healthService.report().database
			call.respond(database.ok.toStatus(), database)
		}

		get("/jobs/{slug}") {
			call.noStore()
			if (!call.requireAuthorized(healthService)) return@get
			val job = healthService.report().jobs[call.parameters["slug"]]
			if (job == null) {
				call.respond(HttpStatusCode.NotFound)
			} else {
				call.respond(job.ok.toStatus(), job)
			}
		}
	}
}

private enum class Caller { ANONYMOUS, AUTHORIZED, REJECTED, UNVERIFIABLE }

private val logger = LoggerFactory.getLogger("HealthRoutes")

/**
 * Only a Bearer credential is ours; any other scheme (e.g. Basic auth meant for a
 * reverse proxy) is treated as anonymous. A wrong or empty Bearer token is REJECTED
 * rather than downgraded, so a misconfigured monitor fails loudly.
 */
private suspend fun ApplicationCall.caller(healthService: HealthService): Caller {
	val header = request.headers[HttpHeaders.Authorization]?.trim() ?: return Caller.ANONYMOUS
	val scheme = header.substringBefore(' ')
	if (!scheme.equals(AuthScheme.Bearer, ignoreCase = true)) return Caller.ANONYMOUS
	val token = header.substringAfter(' ', "").trim()
	if (token.isEmpty()) return Caller.REJECTED
	return try {
		if (healthService.verifyToken(token)) Caller.AUTHORIZED else Caller.REJECTED
	} catch (e: CancellationException) {
		throw e
	} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
		logger.warn("Could not verify health token", e)
		Caller.UNVERIFIABLE
	}
}

/** Responds with the failure and returns false unless the caller presented a valid token. */
private suspend fun ApplicationCall.requireAuthorized(healthService: HealthService): Boolean =
	when (caller(healthService)) {
		Caller.AUTHORIZED -> true
		Caller.UNVERIFIABLE -> {
			respond(HttpStatusCode.ServiceUnavailable)
			false
		}

		Caller.ANONYMOUS, Caller.REJECTED -> {
			respond(HttpStatusCode.Unauthorized)
			false
		}
	}

private fun ApplicationCall.noStore() {
	response.header(HttpHeaders.CacheControl, "no-store")
}

private fun Boolean.toStatus() = if (this) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
