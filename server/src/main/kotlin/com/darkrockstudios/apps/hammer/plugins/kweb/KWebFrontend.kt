package com.darkrockstudios.apps.hammer.plugins.kweb

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.frontend.adminLoginPage
import com.darkrockstudios.apps.hammer.frontend.adminPanelPage
import com.darkrockstudios.apps.hammer.frontend.homePage
import com.darkrockstudios.apps.hammer.frontend.notFoundPage
import com.darkrockstudios.apps.hammer.utilities.ResUtils
import com.darkrockstudios.apps.hammer.utils.getTranslatedLocales
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kweb.*
import kweb.plugins.FaviconPlugin
import kweb.plugins.fomanticUI.fomanticUIPlugin
import kweb.plugins.staticFiles.ResourceFolder
import kweb.plugins.staticFiles.StaticFilesPlugin
import kweb.state.KVal
import kweb.state.KVar
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import java.time.Duration
import kotlin.coroutines.CoroutineContext

fun Application.configureKweb(config: ServerConfig) {
	install(WebSockets) {
		pingPeriod = Duration.ofSeconds(10)
		timeout = Duration.ofSeconds(30)
	}

	val faviconPlugin = FaviconPlugin {
		respondBytes(ResUtils.getResourceAsBytes("assets/favicon.ico"), ContentType.Image.Any)
	}

	install(Kweb) {
		val staticFiles = StaticFilesPlugin(ResourceFolder("/assets"), "/assets")
		plugins = listOf(fomanticUIPlugin, faviconPlugin, staticFiles)
		kwebConfig = hammerKwebConfig
		debug = false
	}

	val availableLocales = getTranslatedLocales()
	val app = this

	installKwebOnRemainingRoutes {
		val ioDispatcher: CoroutineContext by inject(named(DISPATCHER_IO))
		val scope = CoroutineScope(Job() + ioDispatcher)
		val loc = KwebLocalizer(app, doc, config.getDefaultLocale())

		doc.head {
			title().text("Hammer")

			meta(
				name = "viewport",
				content = "width=device-width, initial-scale=1.0, maximum-scale=1.0"
			)
			rellink(LinkRelationship.stylesheet, "/assets/css/base.css")
		}
		doc.body {
			route {
				val accountRepository: AccountsRepository by inject()
				val whitListRepository: WhiteListRepository by inject()

				fun goTo(location: String) {
					url.value = location
				}

				val authToken = KVar<String?>(null)

				homePage(scope, config, whitListRepository, loc, availableLocales, ::goTo)

				adminLoginPage(accountRepository, log, authToken, scope, ::goTo)

				adminPanelPage(accountRepository, authToken, whitListRepository, scope, ::goTo)

				notFound(notFoundPage)
			}
		}
	}
}