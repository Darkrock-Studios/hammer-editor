package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.utilities.ResUtils
import io.ktor.htmx.*
import io.ktor.http.*
import io.ktor.server.htmx.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import kotlin.time.Duration.Companion.days

const val LOCALE_COOKIE_NAME = "locale"

fun Route.localeRoutes() {
	val configRepository: ConfigRepository = application.get()

	hx.post("/locale") {
		val params = call.receiveParameters()
		val localeTag = params["locale"] ?: ""
		val redirectTo = params["redirectTo"]

		val supported = ResUtils.getTranslatedLocales().map { it.toLanguageTag() }.toSet()
		val tag =
			if (supported.contains(localeTag)) localeTag else configRepository.get(AdminServerConfig.DEFAULT_LOCALE)

		call.apply {
			response.cookies.append(
				Cookie(
					name = LOCALE_COOKIE_NAME,
					value = tag,
					path = "/",
					maxAge = 365.days.inWholeSeconds.toInt()
				)
			)

			val back = redirectTo
				?: request.headers[HxRequestHeaders.CurrentUrl]
				?: request.headers[HttpHeaders.Referrer]
				?: "/"
			response.headers.append(HxResponseHeaders.Redirect, back)
			respond(HttpStatusCode.NoContent)
		}
	}
}