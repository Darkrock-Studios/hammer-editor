package com.darkrockstudios.apps.hammer.health

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.get

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
			}
		}

		get("/database") {
			call.noStore()
			if (call.caller(healthService) != Caller.AUTHORIZED) {
				call.respond(HttpStatusCode.Unauthorized)
				return@get
			}
			val database = healthService.report().database
			call.respond(database.ok.toStatus(), database)
		}

		get("/jobs/{slug}") {
			call.noStore()
			if (call.caller(healthService) != Caller.AUTHORIZED) {
				call.respond(HttpStatusCode.Unauthorized)
				return@get
			}
			val job = healthService.report().jobs[call.parameters["slug"]]
			if (job == null) {
				call.respond(HttpStatusCode.NotFound)
			} else {
				call.respond(job.ok.toStatus(), job)
			}
		}
	}
}

private enum class Caller { ANONYMOUS, AUTHORIZED, REJECTED }

/** A wrong or malformed token is REJECTED rather than downgraded, so a misconfigured monitor fails loudly. */
private suspend fun ApplicationCall.caller(healthService: HealthService): Caller {
	if (request.headers[HttpHeaders.Authorization] == null) return Caller.ANONYMOUS
	val header = runCatching { request.parseAuthorizationHeader() }.getOrNull()
	val token = (header as? HttpAuthHeader.Single)
		?.takeIf { it.authScheme.equals(AuthScheme.Bearer, ignoreCase = true) }
		?.blob
		?: return Caller.REJECTED
	return if (healthService.verifyToken(token)) Caller.AUTHORIZED else Caller.REJECTED
}

private fun ApplicationCall.noStore() {
	response.header(HttpHeaders.CacheControl, "no-store")
}

private fun Boolean.toStatus() = if (this) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
