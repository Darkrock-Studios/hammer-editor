package com.darkrockstudios.apps.hammer.plugins.kweb

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_IO
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kweb.Kweb
import kweb.h1
import kweb.installKwebOnRemainingRoutes
import kweb.plugins.fomanticUI.fomanticUIPlugin
import kweb.plugins.staticFiles.ResourceFolder
import kweb.plugins.staticFiles.StaticFilesPlugin
import kweb.route
import kweb.state.KVar
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import java.time.Duration
import kotlin.coroutines.CoroutineContext

fun Application.configureKweb() {
	install(WebSockets) {
		pingPeriod = Duration.ofSeconds(10)
		timeout = Duration.ofSeconds(30)
	}

	install(Kweb) {
		val staticFiles = StaticFilesPlugin(ResourceFolder("/assets"), "/assets")
		plugins = listOf(fomanticUIPlugin, staticFiles)
		kwebConfig = hammerKwebConfig
		debug = false
	}

	installKwebOnRemainingRoutes {
		doc.body {
			route {
				val accountRepository: AccountsRepository by inject()
				val whitListRepository: WhiteListRepository by inject()
				val ioDispatcher: CoroutineContext by inject(named(DISPATCHER_IO))

				val scope = CoroutineScope(Job() + ioDispatcher)

				fun goTo(location: String) {
					url.value = location
				}

				val authToken = KVar<String?>(null)

				adminLoginPage(accountRepository, authToken, scope, ::goTo)

				adminPanelPage(accountRepository, authToken, whitListRepository, scope, ::goTo)

				notFound {
					h1().text("Not Found :(")
				}
			}
		}
	}
}