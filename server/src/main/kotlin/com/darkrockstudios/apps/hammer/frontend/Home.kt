package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.plugins.kweb.KwebStringTranslator
import com.darkrockstudios.apps.hammer.plugins.kweb.src
import com.github.aymanizz.ktori18n.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kweb.*
import kweb.components.Component
import kweb.plugins.fomanticUI.fomantic
import kweb.routing.RouteReceiver
import java.util.*

fun RouteReceiver.homePage(
	scope: CoroutineScope,
	config: ServerConfig,
	whiteListEnabled: WhiteListRepository,
	translator: KwebStringTranslator,
	changeLocale: (Locale, KwebStringTranslator) -> Unit,
	goTo: (String) -> Unit,
) {
	path("/") {
		div(fomantic.pusher) {
			mastHead(goTo)

			div(fomantic.ui.vertical.segment.padded) {
				div(fomantic.ui.middle.aligned.stackable.grid.container) {
					element.text(translator.t(R("greeting")))

					serverMessage(config, whiteListEnabled)

					whatIsHammer()

					hammerFeatures()

					downloadHammer()
				}
			}

			footer(translator, changeLocale)
		}
	}
}

private fun Component.serverMessage(config: ServerConfig, whiteListEnabled: WhiteListRepository) {
	val whiteList = runBlocking { whiteListEnabled.useWhiteList() }

	if (whiteList || config.serverMessage.isNotBlank()) {
		div(fomantic.ui.row.text.container) {
			div(fomantic.padded) {
				div(fomantic.ui.message.warning) {
					if (config.serverMessage.isNotBlank()) {
						div(fomantic.ui.center.aligned.row) {
							div(fomantic.ui.internally.celled.grid) {
								div(fomantic.center.aligned.row) {
									if (config.serverMessage.isNotBlank()) {
										div(fomantic.column) {
											h3(fomantic.ui.header).text("Server Message")
											p().innerHTML(config.serverMessage)
										}
									}
								}
							}
						}
					}

					if (whiteList) {
						div(fomantic.ui.row) {
							div(fomantic.ui.info.message.column).innerHTML("This server requires you to get permission before creating an account. Please contact ${config.contact} to get on the white list.")
						}
					}
				}
			}
		}
	}
}

private fun Component.whatIsHammer() {
	div(fomantic.ui.text.container.row) {
		div(fomantic.padded) {
			h1(fomantic.ui.header).text("What is Hammer?")
			p().innerHTML("Hammer is an open source tool for writing stories, and the worlds in which they exist.")
		}
	}
}

private fun Component.hammerFeatures() {
	div(fomantic.ui.row.four.column.grid) {
		messageColumn(
			"Multi-platform",
			"This app is where ever you are. Your phone, tablet, desktop, laptop, this program can be installed and run, not simply a website-in-a-box, but instead using native client side technologies to provide the best experience possible."
		)

		messageColumn(
			"Multi-Screen",
			"Whether you are on a phone, tablet, or desktop, Hammer will make the best use of your screen space. Also supporting both light and dark modes."
		)

		messageColumn(
			"Offline first",
			"I was frustrated with most of the story writing software I was finding as they were using web technologies (aka: Web Page in a box) which always seems to run into problems while being used offline for long periods of time. Hammer is designed from the ground up to be entirely local, no internet connection required, ever."
		)

		messageColumn(
			"Transparent Data",
			"Your data is yours. It's not stored in the cloud, or some opaque database. It is stored in simple, human readable files, just using files and folders to define the project structure. You can open your OSes file browser and take a look for your self. If this program went away today you would be able to easily interact with your data."
		)
		/*
		div(fomantic.row) {
			messageColumn(
				"Intelligent Syncing",
				"Your data can be synchronized between devices allowing you to work on your story from anywhere, and have no fear of a change on one device, overwriting a change on another device.")

			messageColumn(
				"Self hosted Syncing",
				"Syncing is entirely optional, you can install the client and use it on one device, or you can install the server and sync your data between devices. You could even use some other service to sync your data, like Dropbox or Google Drive. It's all up to you.")
		}
		*/
	}
}

private fun Component.downloadHammer() {
	div(fomantic.ui.text.container.row) {
		div(fomantic.padded) {
			h1(fomantic.ui.dividing.header).text("Get your Hammer:")
		}
	}

	div(fomantic.ui.row.four.column.grid.middle.aligned) {
		downloadColumn(
			"Windows",
			"windows",
			"https://github.com/Wavesonics/hammer-editor/releases/latest/download/hammer.msi"
		)

		downloadColumn(
			"Linux",
			"linux",
			"https://github.com/Wavesonics/hammer-editor/releases/latest/download/hammer_amd64.deb "
		)

		downloadColumn(
			"MacOS",
			"apple",
			"https://github.com/Wavesonics/hammer-editor/releases/latest/download/hammer.dmg "
		)

		downloadColumn(
			"Android",
			"android",
			"https://play.google.com/store/apps/details?id=com.darkrockstudios.apps.hammer.android"
		)

		div(fomantic.ui.three.wide.right.floated.column) {
			img(fomantic.ui.large.image).src("/assets/images/download.png")
		}
	}
}

private fun Component.messageColumn(header: String, body: String) {
	div(fomantic.column) {
		div(fomantic.ui.raised.segment) {
			h3(fomantic.ui.header).text(header)
			p().innerHTML(body)
		}
	}
}

private fun Component.downloadColumn(name: String, icon: String, link: String) {
	div(fomantic.column) {
		div(fomantic.ui.raised.segment) {
			h3(fomantic.ui.header).text(name)
			i(fomantic.icon).addClasses(icon)
			a(href = link).text(
				"Download"
			)
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
	}
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

private fun Component.footer(
	translator: KwebStringTranslator,
	changeLocale: (Locale, KwebStringTranslator) -> Unit,
) {
	div(fomantic.ui.inverted.vertical.segment) {
		div(fomantic.ui.center.aligned.text.container) {
			div(fomantic.row) {
				a(href = "https://github.com/Wavesonics/hammer-editor/") {
					i(fomantic.icon).addClasses("github")
					span().text("GitHub")
				}

				span().text(" ")

				a(href = "https://discord.gg/GTmgjZcupk") {
					i(fomantic.icon).addClasses("discord")
					span().text("Discord")
				}

				p().text("v${BuildMetadata.APP_VERSION}")

				a {
					span().text("en")
				}.on.click {
					changeLocale(Locale.ENGLISH, translator)
				}

				span().text(" ")

				a {
					span().text("de")
				}.on.click {
					changeLocale(Locale.GERMAN, translator)
				}
			}
		}
	}.addClasses("footer")
}