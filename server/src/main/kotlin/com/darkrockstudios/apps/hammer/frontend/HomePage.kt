package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.frontend.utils.canonicalUrl
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.webSiteJsonLd
import com.darkrockstudios.apps.hammer.utilities.MarkdownService
import com.darkrockstudios.apps.hammer.plugin.NoticeSlot
import com.darkrockstudios.apps.hammer.plugin.putAllowedUsersNotice
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

fun Route.homePage(
	configRepository: ConfigRepository,
	markdownService: MarkdownService,
) {
	route("/") {
		get {
			val model = call.withDefaults()
			model["page_stylesheet"] = "/assets/css/home.css"
			model["preloadImages"] = MASTHEAD_PRELOADS
			val serverMessageHtml = markdownService.markdownToSafeHtml(
				configRepository.get(AdminServerConfig.SERVER_MESSAGE)
			)
			val contactEmail = configRepository.get(AdminServerConfig.CONTACT_EMAIL)
			val bannerNotice = call.putAllowedUsersNotice(
				model, NoticeSlot.HOME_BANNER,
				htmlKey = "allowedUsersBannerHtml", providedKey = "allowedUsersBannerProvided",
			)

			model["serverMessageHtml"] = serverMessageHtml
			model["page_script"] = "/assets/js/home.js"
			model["title"] = "Hammer — ${call.msg("home_meta_tagline")}"
			model["metaDescription"] = call.msg("home_meta_description")
			model["jsonLd"] = webSiteJsonLd(
				name = "Hammer",
				url = call.canonicalUrl("/"),
				description = call.msg("home_meta_description"),
			)

			val showAllowedUsersNotice = contactEmail.isNotBlank() && bannerNotice == null
			if (showAllowedUsersNotice) {
				model["allowedUsersNotice"] = true
				call.msg(model, "home_servermessage_allowedusers", contactEmail)
			}
			model["hasInstanceNotice"] =
				serverMessageHtml.isNotBlank() || showAllowedUsersNotice || !bannerNotice.isNullOrBlank()

			call.respond(MustacheContent("home.mustache", model))
		}
	}
}

