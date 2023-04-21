package com.darkrockstudios.apps.hammer.plugins.kweb

import kotlinx.coroutines.CoroutineScope
import kweb.div
import kweb.h1
import kweb.plugins.fomanticUI.fomantic
import kweb.routing.RouteReceiver

fun RouteReceiver.homePage(
	scope: CoroutineScope,
	goTo: (String) -> Unit,
) {
	path("/") {
		div(fomantic.ui.middle.aligned.center.aligned.grid)
		{
			div(fomantic.column) {
				div(fomantic.ui.text.message) {
					h1().text("Hammer")
				}
			}
		}
	}
}