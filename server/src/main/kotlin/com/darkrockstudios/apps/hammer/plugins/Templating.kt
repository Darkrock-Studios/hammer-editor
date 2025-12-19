package com.darkrockstudios.apps.hammer.plugins

import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.server.application.*
import io.ktor.server.mustache.*

fun Application.configureTemplating() {
	install(Mustache) {
		mustacheFactory = DefaultMustacheFactory("templates")
	}
}
