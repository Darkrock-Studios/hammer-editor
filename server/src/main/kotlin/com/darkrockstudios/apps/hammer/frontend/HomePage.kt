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

/**
 * The masthead is the home page's LCP element but is painted as a CSS background, so the browser
 * can't discover it until home.css parses. These preloads pull it forward; the `media` queries are
 * exact complements of the `.masthead` breakpoint so only one variant is ever fetched.
 *
 * Hrefs must match the CSS-issued request byte for byte — adding a cache-busting query here would
 * make the browser download the image twice.
 */
private val MASTHEAD_PRELOADS = listOf(
	mapOf(
		"href" to "/assets/images/masthead-960.webp",
		"type" to "image/webp",
		"media" to "(max-width: 768px)",
	),
	mapOf(
		"href" to "/assets/images/masthead-1920.webp",
		"type" to "image/webp",
		"media" to "not all and (max-width: 768px)",
	),
)

/**
 * The origin story's photograph is optional: the section renders as copy alone until the
 * asset is added, rather than showing a broken image. Resolved once, not per request.
 */
private val ORIGIN_PHOTO_PRESENT: Boolean =
	object {}.javaClass.getResource("/assets/images/origin-van-760.webp") != null

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
			model["preloadImages"] = MASTHEAD_PRELOADS
			val useWhiteList = whiteListRepository.useWhiteList()
			val serverMessage = configRepository.get(AdminServerConfig.SERVER_MESSAGE)
			val contactEmail = configRepository.get(AdminServerConfig.CONTACT_EMAIL)
			val patreonConfig = configRepository.get(AdminServerConfig.PATREON_CONFIG)
			val patreonFeatureEnabled = serverConfig.patreonEnabled == true
			val patreonActive = patreonFeatureEnabled && patreonConfig.enabled && patreonConfig.patreonUrl.isNotBlank()

			model["serverMessage"] = serverMessage
			model["page_script"] = "/assets/js/home.js"
			model["title"] = "Hammer — ${call.msg("home_meta_tagline")}"
			model["metaDescription"] = call.msg("home_meta_description")
			model["hasOriginPhoto"] = ORIGIN_PHOTO_PRESENT
			model["jsonLd"] = webSiteJsonLd(
				name = "Hammer",
				url = call.canonicalUrl("/"),
				description = call.msg("home_meta_description"),
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

