package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.frontend.utils.setLocaleAndRedirect
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Route.localeRoutes() {
	post("/locale") {
		val params = call.receiveParameters()
		val newLocale = params["locale"] ?: ""
		val redirectTo = params["redirectTo"]
		call.setLocaleAndRedirect(newLocale, redirectTo)
	}
}