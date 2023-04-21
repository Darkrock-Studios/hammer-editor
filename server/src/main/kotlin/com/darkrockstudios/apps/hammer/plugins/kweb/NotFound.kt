package com.darkrockstudios.apps.hammer.plugins.kweb

import kweb.div
import kweb.h2
import kweb.plugins.fomanticUI.fomantic
import kweb.routing.NotFoundReceiver

val notFoundPage: NotFoundReceiver = { path ->
	div(fomantic.ui.text.container) {
		div(fomantic.ui.middle.aligned.centered.aligned.padded.grid)
		{
			div(fomantic.ui.text.message.error) {
				h2().text("404 Not Found")
			}
		}
	}
}