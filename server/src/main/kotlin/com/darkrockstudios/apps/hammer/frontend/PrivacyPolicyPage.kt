package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.PrivacyPolicyRepository
import com.darkrockstudios.apps.hammer.utilities.MarkdownRenderer
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.privacyPolicyPage() {
	val privacyPolicyRepository: PrivacyPolicyRepository by inject()
	val markdownRenderer: MarkdownRenderer by inject()
	route("/privacy") {
		get {
			val markdown = privacyPolicyRepository.text()
			if (markdown == null) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			val model = call.withDefaults()
			model["page_stylesheet"] = "/assets/css/about.css"
			model["privacyHtml"] = markdownRenderer.markdownToSafeHtml(markdown)

			call.respond(MustacheContent("privacy.mustache", model))
		}
	}
}
