package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.frontend.utils.withMessages
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.stream.createHTML

fun Route.homeRoutes() {
	route("/") {
		get {
			val model = mapOf(
				"title" to "Hammer",
				"greeting" to "Hello, World!",
				"message" to "Welcome to Hammer Server"
			)
			call.respond(MustacheContent("index.mustache", call.withMessages(model)))
		}

		get("/clicked") {
			// This endpoint is intended to be called via HTMX and returns a small HTML fragment
			val ts = System.currentTimeMillis()
			val html = createHTML().p {
				+"You clicked at $ts"
				div {

				}
			}

			// Optional: trigger a client-side event consumers can listen to via HTMX
			// Using the standard HX-Trigger header (no extra dependencies required)
			call.response.headers.append("HX-Trigger", "{\"clicked\":{\"ts\":$ts}}")

			// Respond with the fragment for HTMX to swap into the page
			call.respondText(html, ContentType.Text.Html)
		}
	}
}