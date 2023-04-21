package com.darkrockstudios.apps.hammer.plugins.kweb

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.utilities.ResUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.serialization.json.JsonPrimitive
import kweb.*
import kweb.plugins.FaviconPlugin
import kweb.plugins.fomanticUI.fomanticUIPlugin
import kweb.plugins.staticFiles.ResourceFolder
import kweb.plugins.staticFiles.StaticFilesPlugin
import kweb.state.KVar
import kweb.util.json
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import java.time.Duration
import kotlin.coroutines.CoroutineContext

fun Application.configureKweb() {
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

	installKwebOnRemainingRoutes {
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
				val ioDispatcher: CoroutineContext by inject(named(DISPATCHER_IO))

				val scope = CoroutineScope(Job() + ioDispatcher)

				fun goTo(location: String) {
					url.value = location
				}

				val authToken = KVar<String?>(null)

				homePage(scope, ::goTo)

				adminLoginPage(accountRepository, authToken, scope, ::goTo)

				adminPanelPage(accountRepository, authToken, whitListRepository, scope, ::goTo)

				notFound(notFoundPage)
			}
		}
	}
}

fun ElementCreator<Element>.rellink(
	rel: LinkRelationship,
	href: String,
	hreflang: String? = null,
	attributes: Map<String, JsonPrimitive> = emptyMap(),
): Element {
	return LinkElement(
		element(
			"link",
			attributes = attributes
				.set("rel", rel.name.json)
				.set("href", href.json)
				.set("hreflang", JsonPrimitive(hreflang))
		)
	)
}