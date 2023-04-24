package com.darkrockstudios.apps.hammer.plugins.kweb

import kotlinx.coroutines.CoroutineScope
import kweb.*
import kweb.components.Component
import kweb.plugins.fomanticUI.fomantic
import kweb.routing.RouteReceiver

fun RouteReceiver.homePage(
	scope: CoroutineScope,
	goTo: (String) -> Unit,
) {
	path("/") {
		div(fomantic.pusher) {
			mastHead(goTo)

			div(fomantic.ui.vertical.segment.padded) {
				div(fomantic.ui.middle.aligned.stackable.grid.container) {

					div(fomantic.ui.info.message.text.container).innerHTML("Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s.")

					div(fomantic.row.text.container) {
						div(fomantic.six.wide.column) {
							div(fomantic.ui.raised.segment) {
								h3(fomantic.ui.header).text("Lorem Ipsum")
								p().innerHTML("Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged.")
							}

							div(fomantic.ui.raised.segment) {
								h3(fomantic.ui.header).text("Lorem Ipsum")
								p().innerHTML("Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged.")
							}
						}
						div(fomantic.four.wide.right.floated.column) {
							img(fomantic.item).src("/assets/images/hammer_icon.png")
						}
					}

					div(fomantic.row.text.container) {

					}
				}
			}

			div(fomantic.ui.inverted.vertical.segment) {
				div(fomantic.ui.center.aligned.container).innerHTML("First Alpha Test Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s.")
			}.addClasses("footer")
		}
	}
}

private fun Component.mastHead(goTo: (String) -> Unit) {
	div(fomantic.ui.inverted.vertical.masthead.center.aligned.middle.aligned.segment) {
		menu(goTo)

		div(fomantic.ui.text.container.center.aligned) {
			h1(fomantic.ui.inverted.header).text("Hammer:")
			h2().text("A simple tool for building stories")
		}
	}.addClasses("screen-height")
}

private fun Component.menu(goTo: (String) -> Unit) {
	div(fomantic.ui.container) {
		div(fomantic.ui.large.secondary.inverted.pointing.menu) {
			img(fomantic.item).src("/assets/images/hammer_icon_sm.png")
			a(fomantic.item.text.header).text("Hammer")
			div(fomantic.right.item) {
				a(fomantic.ui.inverted.button).text("Admin").on.click {
					goTo("/admin")
				}
			}
		}
	}
}