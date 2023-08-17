package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.plugins.ADMIN_AUTH
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH
import io.ktor.http.*
import io.ktor.server.application.*
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
				HttpResponseError(error = "Missing Parameter", message = "email was missing")
			)
		} else {
			val result = adminRepository.addToWhiteList(email)
			if (result.isSuccess) {
				call.respond("Success")
			} else {
				val e = result.exceptionOrNull()
				call.respond(
					status = HttpStatusCode.InternalServerError,
					HttpResponseError(error = "Error", message = e?.message ?: "Unknown failure")
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
				HttpResponseError(error = "Missing Parameter", message = "email was missing")
			)
		} else {
			adminRepository.removeFromWhiteList(email)
			call.respond("Success")
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
				HttpResponseError(error = "Missing Parameter", message = "setEnable was missing")
			)
		} else {
			if (setEnable) {
				adminRepository.enableWhiteList()
			} else {
				adminRepository.disableWhiteList()
			}
			call.respond("Success")
		}
	}
}