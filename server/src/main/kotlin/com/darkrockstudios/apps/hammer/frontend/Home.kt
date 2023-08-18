package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.plugins.kweb.KwebLocalizer
import com.darkrockstudios.apps.hammer.plugins.kweb.src
import com.darkrockstudios.apps.hammer.plugins.kweb.text
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
	loc: KwebLocalizer,
	availableLocales: List<Locale>,
	goTo: (String) -> Unit,
) {
	path("/") {
		div(fomantic.pusher) {
			mastHead(goTo, loc)

			div(fomantic.ui.vertical.segment.padded) {
				div(fomantic.ui.middle.aligned.stackable.grid.container) {

					serverMessage(config, whiteListEnabled, loc)

					whatIsHammer(loc)

					hammerFeatures(loc)

					downloadHammer(loc)
				}
			}

			footer(loc, availableLocales, loc)
		}
	}
}

private fun Component.serverMessage(config: ServerConfig, whiteListEnabled: WhiteListRepository, loc: KwebLocalizer) {
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
											h3(fomantic.ui.header).text("home.servermessage", loc)
											p().innerHTML(config.serverMessage)
										}
									}
								}
							}
						}
					}

					if (whiteList) {
						div(fomantic.ui.row) {
							div(fomantic.ui.info.message.column).innerHTML(
								loc.t(
									"home.servermessage.whitelist",
									config.contact ?: "[foo@bar.com]"
								)
							)
						}
					}
				}
			}
		}
	}
}

private fun Component.whatIsHammer(loc: KwebLocalizer) {
	div(fomantic.ui.text.container.row) {
		div(fomantic.padded) {
			h1(fomantic.ui.header).text("home.whatishammer.title", loc)
			p().innerHTML(loc.t("home.whatishammer.subtitle"))
		}
	}
}

private fun Component.hammerFeatures(loc: KwebLocalizer) {
	div(fomantic.ui.row.four.column.grid) {
		messageColumn(
			R("home.feature.multiplatform.header"),
			R("home.feature.multiplatform.body"),
			loc
		)

		messageColumn(
			R("home.feature.multiscreen.header"),
			R("home.feature.multiscreen.body"),
			loc
		)

		messageColumn(
			R("home.feature.offlinefirst.header"),
			R("home.feature.offlinefirst.body"),
			loc
		)

		messageColumn(
			R("home.feature.transparentdata.header"),
			R("home.feature.transparentdata.body"),
			loc
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

private fun Component.downloadHammer(loc: KwebLocalizer) {
	div(fomantic.ui.text.container.row) {
		div(fomantic.padded) {
			h1(fomantic.ui.dividing.header).text(loc.t(R("home.download.header")))
		}
	}

	div(fomantic.ui.row.four.column.grid.middle.aligned) {
		downloadColumn(
			R("home.download.windows"),
			"windows",
			"https://github.com/Wavesonics/hammer-editor/releases/latest/download/hammer.msi",
			loc
		)

		downloadColumn(
			R("home.download.linux"),
			"linux",
			"https://github.com/Wavesonics/hammer-editor/releases/latest/download/hammer_amd64.deb ",
			loc
		)

		downloadColumn(
			R("home.download.macos"),
			"apple",
			"https://github.com/Wavesonics/hammer-editor/releases/latest/download/hammer.dmg ",
			loc
		)

		downloadColumn(
			R("home.download.android"),
			"android",
			"https://play.google.com/store/apps/details?id=com.darkrockstudios.apps.hammer.android",
			loc
		)

		div(fomantic.ui.three.wide.right.floated.column) {
			img(fomantic.ui.large.image).src("/assets/images/download.png")
		}
	}
}

private fun Component.messageColumn(header: R, body: R, loc: KwebLocalizer) {
	div(fomantic.column) {
		div(fomantic.ui.raised.segment) {
			h3(fomantic.ui.header).text(loc.t(header))
			p().innerHTML(loc.t(body))
		}
	}
}

private fun Component.downloadColumn(name: R, icon: String, link: String, loc: KwebLocalizer) {
	div(fomantic.column) {
		div(fomantic.ui.raised.segment) {
			h3(fomantic.ui.header).text(loc.t(name))
			i(fomantic.icon).addClasses(icon)
			a(href = link).text("home.download.link", loc)
		}
	}
}

private fun Component.mastHead(goTo: (String) -> Unit, loc: KwebLocalizer) {
	div(fomantic.ui.inverted.vertical.masthead.center.aligned.middle.aligned.segment) {
		menu(goTo, loc)

		div(fomantic.ui.text.container.center.aligned) {
			h1(fomantic.ui.inverted.header).text("home.masthead.title", loc)
			h2().text("home.masthead.subtitle", loc)
		}
	}
}

private fun Component.menu(goTo: (String) -> Unit, loc: KwebLocalizer) {
	div(fomantic.ui.container) {
		div(fomantic.ui.large.secondary.inverted.pointing.menu) {
			img(fomantic.item).src("/assets/images/hammer_icon_sm.png")
			a(fomantic.item.text.header).text("home.header.title", loc)
			div(fomantic.right.item) {
				a(fomantic.ui.inverted.button).text("home.header.adminbutton", loc).on.click {
					goTo("/admin")
				}
			}
		}
	}
}

private fun Component.footer(translator: KwebLocalizer, availableLocales: List<Locale>, loc: KwebLocalizer) {
	div(fomantic.ui.inverted.vertical.segment) {
		div(fomantic.ui.center.aligned.text.container) {
			div(fomantic.row) {
				a(href = "https://github.com/Wavesonics/hammer-editor/") {
					i(fomantic.icon).addClasses("github")
					span().text("home.footer.github", loc)
				}

				span().text(" ")

				a(href = "https://discord.gg/GTmgjZcupk") {
					i(fomantic.icon).addClasses("discord")
					span().text("home.footer.discord", loc)
				}

				p().text("v${BuildMetadata.APP_VERSION}")

				span().text(" ")

				for (locale in availableLocales) {
					a(href = this.browser.url.value) {
						span().text(locale.toLanguageTag())
					}.on.click {
						translator.overrideLocale(locale)
					}

					span().text(" ")
				}

				span().text(" ")

				span().text("[${loc.t(R("language"))}]")
			}
		}
	}.addClasses("footer")
}