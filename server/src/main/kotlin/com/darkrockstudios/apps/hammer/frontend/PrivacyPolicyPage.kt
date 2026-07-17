package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.PrivacyPolicyRepository
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.privacyPolicyPage(
	privacyPolicyRepository: PrivacyPolicyRepository,
) {
	route("/privacy") {
		get {
			val text = privacyPolicyRepository.text()
			if (text == null) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			val model = call.withDefaults()
			model["privacyText"] = text

			call.respond(MustacheContent("privacy.mustache", model))
		}
	}
}
