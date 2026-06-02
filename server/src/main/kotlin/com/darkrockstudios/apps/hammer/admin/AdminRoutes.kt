package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.monitoring.MetricsRepository
import com.darkrockstudios.apps.hammer.monitoring.MonitoringState
import com.darkrockstudios.apps.hammer.monitoring.PrometheusExporter
import com.darkrockstudios.apps.hammer.plugins.ADMIN_AUTH
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH
import com.darkrockstudios.apps.hammer.utilities.ERR_KEY_UNKNOWN
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utilities.respondMissingParameter
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.t
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

private const val ERR_KEY_WHITELIST_EMAIL_MISSING = "api_admin_whitelist_error_emailmissing"
private const val ERR_KEY_ENABLE_WHITELIST_MISSING = "api_admin_enablewhitelist_enablemissing"
private const val MSG_SUCCESS_KEY = "api_success"

fun Route.adminRoutes() {
	authenticate(USER_AUTH, ADMIN_AUTH) {
		route("/admin/{userId}") {
			getWhiteList()
			addToWhiteList()
			removeFromWhiteList()
			enableWhiteList()
			prometheusMetrics()
		}
	}
}

/**
 * Prometheus scrape endpoint, gated on the runtime `prometheusEndpointEnabled`
 * toggle (returns 404 when off) and behind admin auth. Configure a scraper with
 * an admin's bearer token against /api/admin/{adminUserId}/metrics.
 */
private fun Route.prometheusMetrics() {
	val metricsRepository: MetricsRepository = get()
	val monitoringState: MonitoringState = get()
	val clock: Clock = get()

	get("/metrics") {
		if (!monitoringState.prometheusEnabled) {
			call.respond(HttpStatusCode.NotFound)
			return@get
		}
		val text = PrometheusExporter.render(metricsRepository.getMergedEndpoints(clock.now() - 24.hours))
		call.respondText(text, ContentType.parse("text/plain; version=0.0.4; charset=utf-8"))
	}
}

private fun Route.getWhiteList() {
	val adminComponent: AdminComponent = get()

	get("/whitelist") {
		val list = adminComponent.getWhiteList()
		call.respond(list)
	}
}

private fun Route.addToWhiteList() {
	val adminRepository: AdminComponent = get()

	put("/whitelist") {
		val email = call.request.queryParameters["email"]
		if (email == null) {
			call.respondMissingParameter(ERR_KEY_WHITELIST_EMAIL_MISSING)
			return@put
		}

		val result = adminRepository.addToWhiteList(email)
		if (isSuccess(result)) {
			call.respond("Success")
		} else {
			call.respond(
				status = HttpStatusCode.InternalServerError,
				HttpResponseError(
					error = "invalid email",
					displayMessage = result.displayMessageText(call, R(ERR_KEY_UNKNOWN)),
				),
			)
		}
	}
}

private fun Route.removeFromWhiteList() {
	val adminRepository: AdminComponent = get()

	delete("/whitelist") {
		val email = call.request.queryParameters["email"]
		if (email == null) {
			call.respondMissingParameter(ERR_KEY_WHITELIST_EMAIL_MISSING)
			return@delete
		}

		adminRepository.removeFromWhiteList(email)
		call.respond(call.t(R(MSG_SUCCESS_KEY)))
	}
}

private fun Route.enableWhiteList() {
	val adminRepository: AdminComponent = get()

	delete("/whitelist/enable/{setEnable}") {
		val setEnable = call.request.queryParameters["setEnable"]?.toBoolean()
		if (setEnable == null) {
			call.respondMissingParameter(ERR_KEY_ENABLE_WHITELIST_MISSING)
			return@delete
		}

		if (setEnable) {
			adminRepository.enableWhiteList()
		} else {
			adminRepository.disableWhiteList()
		}
		call.respond(call.t(R(MSG_SUCCESS_KEY)))
	}
}
