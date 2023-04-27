package com.darkrockstudios.apps.hammer.frontend

import kweb.div
import kweb.h2
import kweb.plugins.fomanticUI.fomantic
import kweb.routing.NotFoundReceiver

val notFoundPage: NotFoundReceiver = { path ->
	div(fomantic.ui.middle.aligned.center.aligned.grid) {
		div(fomantic.center.aligned.column) {
			div(fomantic.ui.text.message.error) {
				h2().text("404 Not Found")
			}
		}.addClasses("medium-width")
	}.addClasses("centered-container")
}