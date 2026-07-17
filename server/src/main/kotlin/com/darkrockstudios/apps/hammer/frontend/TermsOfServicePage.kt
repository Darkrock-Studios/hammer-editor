package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.TermsOfServiceRepository
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.termsOfServicePage(
	termsOfServiceRepository: TermsOfServiceRepository,
) {
	route("/terms") {
		get {
			val challenge = termsOfServiceRepository.challenge()
			if (challenge == null) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			val model = call.withDefaults()
			model["termsText"] = challenge.text

			call.respond(MustacheContent("terms.mustache", model))
		}
	}
}
