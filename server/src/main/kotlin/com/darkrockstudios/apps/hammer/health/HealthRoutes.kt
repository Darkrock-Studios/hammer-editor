package com.darkrockstudios.apps.hammer.health

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.plugins.origin
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

/**
 * Health checks for uptime monitors. Mounted under the API prefix, so the full
 * paths are [HEALTH_ROUTE], `/database` and `/jobs/{slug}` beneath it.
 *
 * Every path answers 200 when what it reports is healthy and 503 when not. Without
 * a token only the overall summary is available. A bearer token issued from the admin
 * Jobs page unlocks the full report and the per-check paths, whose status code
 * reflects that one check alone.
 */
// Relative to the API prefix. Also the log throttle keys, so they always match the routes.
private const val ROOT = "/health"
private const val DATABASE = "$ROOT/database"
private const val JOB = "$ROOT/jobs/{slug}"

fun Route.healthRoutes() {
	val healthService: HealthService = get()

	get(ROOT) {
		call.noStore()
		when (val caller = call.caller(healthService)) {
			Caller.Anonymous -> {
				val report = healthService.report()
				call.respondHealth(report.ok.toStatus(), report.summary(), ROOT, report.failureReason())
			}

			Caller.Authorized -> {
				val report = healthService.report()
				call.respondHealth(report.ok.toStatus(), report, ROOT, report.failureReason())
			}

			is Caller.Rejected, Caller.Unverifiable -> call.respondUnauthorized(caller, ROOT)
		}
	}

	get(DATABASE) {
		call.noStore()
		val caller = call.caller(healthService)
		if (caller != Caller.Authorized) return@get call.respondUnauthorized(caller, DATABASE)
		val database = healthService.report().database
		call.respondHealth(database.ok.toStatus(), database, DATABASE, database.status.name.lowercase())
	}

	get(JOB) {
		call.noStore()
		val caller = call.caller(healthService)
		if (caller != Caller.Authorized) return@get call.respondUnauthorized(caller, JOB)
		val slug = call.parameters["slug"]
		val job = healthService.report().jobs[slug]
		if (job == null) {
			call.logOutcome(HttpStatusCode.NotFound, JOB, "unknown job")
			call.respond(HttpStatusCode.NotFound)
		} else {
			// A registered slug, so still a bounded throttle key.
			call.respondHealth(job.ok.toStatus(), job, "$ROOT/jobs/$slug", job.status.name.lowercase())
		}
	}
}

private sealed interface Caller {
	data object Anonymous : Caller
	data object Authorized : Caller
	data class Rejected(val reason: String) : Caller

	/** The token couldn't be checked, almost always because the database is unreachable. */
	data object Unverifiable : Caller
}

private val logger = LoggerFactory.getLogger("HealthRoutes")

// Health paths are left out of call logging since probes repeat every few seconds, so this is
// the record of failing checks and misconfigured monitors: once a minute per route and outcome.
// Keys use route templates and registered slugs, never the raw path, so a client scanning
// made-up paths can't multiply the log lines.
private val outcomeLog = LogThrottle(1.minutes)

/**
 * Only a Bearer credential is ours; any other scheme (e.g. Basic auth meant for a
 * reverse proxy) is treated as anonymous. A wrong or empty Bearer token is rejected
 * rather than downgraded, so a misconfigured monitor fails loudly.
 */
private suspend fun ApplicationCall.caller(healthService: HealthService): Caller {
	val raw = request.headers[HttpHeaders.Authorization] ?: return Caller.Anonymous
	val isBearer = raw.trimStart().startsWith(AuthScheme.Bearer, ignoreCase = true)
	val header = runCatching { request.parseAuthorizationHeader() }.getOrNull()
	if (header == null) return if (isBearer) Caller.Rejected("malformed bearer header") else Caller.Anonymous
	if (!header.authScheme.equals(AuthScheme.Bearer, ignoreCase = true)) return Caller.Anonymous
	val token = (header as? HttpAuthHeader.Single)?.blob?.takeIf { it.isNotBlank() }
		?: return Caller.Rejected("empty bearer token")
	return try {
		if (healthService.verifyToken(token)) Caller.Authorized else Caller.Rejected("wrong token")
	} catch (e: CancellationException) {
		throw e
	} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
		if (outcomeLog.shouldLog("token-read")) logger.warn("Could not verify a health token", e)
		Caller.Unverifiable
	}
}

private suspend fun ApplicationCall.respondUnauthorized(caller: Caller, route: String) {
	when (caller) {
		Caller.Unverifiable ->
			respondHealth(HttpStatusCode.ServiceUnavailable, HealthUnavailable(), route, "token unavailable")

		is Caller.Rejected -> {
			logOutcome(HttpStatusCode.Unauthorized, route, caller.reason)
			respond(HttpStatusCode.Unauthorized)
		}

		Caller.Anonymous -> {
			logOutcome(HttpStatusCode.Unauthorized, route, "no token")
			respond(HttpStatusCode.Unauthorized)
		}

		Caller.Authorized -> error("An authorized caller reached respondUnauthorized")
	}
}

/** Sends [body] with [status], logging a non-200 with [reason] under [route]. */
private suspend inline fun <reified T : Any> ApplicationCall.respondHealth(
	status: HttpStatusCode,
	body: T,
	route: String,
	reason: String,
) {
	if (status != HttpStatusCode.OK) logOutcome(status, route, reason)
	respond(status, body)
}

// The client is origin.remoteAddress, which is the proxy's address unless trustProxyForwarding
// is on; the key leaves it out so that case still throttles per route and outcome.
private fun ApplicationCall.logOutcome(status: HttpStatusCode, route: String, reason: String) {
	if (outcomeLog.shouldLog("${status.value} $route $reason")) {
		logger.warn(
			"Health check {} answered {} ({}) to {}",
			request.path(), status.value, reason, request.origin.remoteAddress,
		)
	}
}

private fun HealthReport.failureReason(): String {
	val failing = buildList {
		if (!database.ok) add("database ${database.status.name.lowercase()}")
		jobs.filterValues { !it.ok }.forEach { (slug, job) -> add("$slug ${job.status.name.lowercase()}") }
	}
	return failing.joinToString(", ").ifEmpty { "ok" }
}

private fun ApplicationCall.noStore() {
	response.header(HttpHeaders.CacheControl, "no-store")
}

private fun Boolean.toStatus() = if (this) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
