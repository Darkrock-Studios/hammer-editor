package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.utils.canonicalUrl
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.webSiteJsonLd
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.homePage(
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	serverConfig: ServerConfig,
	accountsRepository: AccountsRepository,
	projectAccessRepository: ProjectAccessRepository
) {
	route("/") {
		get {
			val model = call.withDefaults()
			model["page_stylesheet"] = "/assets/css/home.css"
			val useWhiteList = whiteListRepository.useWhiteList()
			val serverMessage = configRepository.get(AdminServerConfig.SERVER_MESSAGE)
			val contactEmail = configRepository.get(AdminServerConfig.CONTACT_EMAIL)
			val patreonConfig = configRepository.get(AdminServerConfig.PATREON_CONFIG)
			val patreonFeatureEnabled = serverConfig.patreonEnabled == true
			val patreonActive = patreonFeatureEnabled && patreonConfig.enabled && patreonConfig.patreonUrl.isNotBlank()

			model["serverMessage"] = serverMessage
			model["page_script"] = "/assets/js/home.js"
			model["jsonLd"] = webSiteJsonLd(
				name = "Hammer",
				url = call.canonicalUrl("/"),
				description = call.msg("home_whatishammer_subtitle"),
			)

			if (useWhiteList && contactEmail.isNotBlank() && !patreonActive) {
				model["whitelistEnabled"] = useWhiteList
				call.msg(model, "home_servermessage_whitelist", contactEmail)
			}

			populateCommunityCalloutModel(serverConfig, model, accountsRepository, projectAccessRepository)

			call.respond(MustacheContent("home.mustache", model))
		}
	}
}

