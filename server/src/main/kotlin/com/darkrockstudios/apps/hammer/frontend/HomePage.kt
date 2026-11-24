package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.HomeConfig
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.frontend.utils.canonicalUrl
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.webSiteJsonLd
import com.darkrockstudios.apps.hammer.utilities.MarkdownRenderer
import com.darkrockstudios.apps.hammer.plugin.NoticeSlot
import com.darkrockstudios.apps.hammer.plugin.putAllowedUsersNotice
import com.darkrockstudios.apps.hammer.services.blogfeed.BlogFeed
import com.darkrockstudios.apps.hammer.services.blogfeed.BlogFeedRepository
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.format.DateTimeFormatter

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

/** Must match the App Store links in home.mustache. Drives the iOS Safari smart app banner. */
private const val IOS_APP_STORE_ID = "6770841038"

private const val PRINCIPLE_LOCAL_URL = "https://hammer.ink/blog/principles/local-first"
private const val PRINCIPLE_TELEMETRY_URL = "https://hammer.ink/blog/principles/no-telemetry"
private const val PRINCIPLE_SAFETY_URL = "https://hammer.ink/blog/principles/data-safety"

/** Already linked from their own cards, so the feed's featured list leaves them out. */
private val LINKED_PRINCIPLE_URLS = setOf(PRINCIPLE_LOCAL_URL, PRINCIPLE_TELEMETRY_URL, PRINCIPLE_SAFETY_URL)

private const val LATEST_POST_COUNT = 3
private val POST_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy")

fun Route.homePage(
	configRepository: ConfigRepository,
	markdownRenderer: MarkdownRenderer,
	homeConfig: HomeConfig,
	blogFeedRepository: BlogFeedRepository,
) {
	route("/") {
		get {
			val model = call.withDefaults()
			model["page_stylesheet"] = "/assets/css/home.css"
			model["preloadImages"] = MASTHEAD_PRELOADS
			model["mastheadCredit"] = true
			val serverMessageHtml = markdownRenderer.markdownToSafeHtml(
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
			model["iosAppStoreId"] = IOS_APP_STORE_ID
			model["principleLocalUrl"] = PRINCIPLE_LOCAL_URL
			model["principleTelemetryUrl"] = PRINCIPLE_TELEMETRY_URL
			model["principleSafetyUrl"] = PRINCIPLE_SAFETY_URL
			model.putAll(externalContentModel(homeConfig, blogFeedRepository.current()))
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

private fun externalContentModel(config: HomeConfig, feed: BlogFeed?): Map<String, Any> {
	val (featured, news) = feed?.posts.orEmpty().partition { it.inCategory(config.blogFeaturedCategory) }
	val morePrinciples = featured
		.filter { it.url.trimEnd('/') !in LINKED_PRINCIPLE_URLS }
		.map { mapOf("title" to it.title, "url" to it.url) }
	val blogPosts = news.take(LATEST_POST_COUNT).map { post ->
		mapOf(
			"title" to post.title,
			"url" to post.url,
			"date" to post.published?.format(POST_DATE_FORMAT).orEmpty(),
			"isoDate" to post.published?.toLocalDate()?.toString().orEmpty(),
		)
	}

	val model = mutableMapOf<String, Any>(
		"morePrinciples" to morePrinciples,
		"hasMorePrinciples" to morePrinciples.isNotEmpty(),
		"blogPosts" to blogPosts,
		"hasBlogPosts" to blogPosts.isNotEmpty(),
		"hasLearn" to (blogPosts.isNotEmpty() || config.tutorialsUrl != null),
	)
	feed?.siteUrl?.let { model["blogUrl"] = it }
	config.tutorialsUrl?.let { model["tutorialsUrl"] = it }
	return model
}
