package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.plugins.ADMIN_AUTH
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.t
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get

fun Route.adminRoutes() {
	authenticate(USER_AUTH, ADMIN_AUTH) {
		route("/admin/{userId}") {
			getWhiteList()
			addToWhiteList()
			removeFromWhiteList()
			enableWhiteList()
		}
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
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api_admin_whitelist_error_emailmissing"))
				)
			)
		} else {
			val result = adminRepository.addToWhiteList(email)
			if (isSuccess(result)) {
				call.respond("Success")
			} else {
				call.respond(
					status = HttpStatusCode.InternalServerError,
					HttpResponseError(
						error = "invalid email",
						displayMessage = result.displayMessageText(call, R("api_error_unknown"))
					)
				)
			}
		}
	}
}

private fun Route.removeFromWhiteList() {
	val adminRepository: AdminComponent = get()

	delete("/whitelist") {
		val email = call.request.queryParameters["email"]
		if (email == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api_admin_whitelist_error_emailmissing"))
				)
			)
		} else {
			adminRepository.removeFromWhiteList(email)
			call.respond(call.t(R("api_success")))
		}
	}
}

private fun Route.enableWhiteList() {
	val adminRepository: AdminComponent = get()

	delete("/whitelist/enable/{setEnable}") {
		val setEnable = call.request.queryParameters["setEnable"]?.toBoolean()
		if (setEnable == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing Parameter",
					displayMessage = call.t(R("api_admin_enablewhitelist_enablemissing"))
				)
			)
		} else {
			if (setEnable) {
				adminRepository.enableWhiteList()
			} else {
				adminRepository.disableWhiteList()
			}
			call.respond(call.t(R("api_success")))
		}
	}
}