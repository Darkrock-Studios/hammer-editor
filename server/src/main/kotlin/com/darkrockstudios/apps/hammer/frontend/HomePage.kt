package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.HammerLinks
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.frontend.utils.canonicalUrl
import com.darkrockstudios.apps.hammer.frontend.utils.getLocale
import com.darkrockstudios.apps.hammer.frontend.utils.localizedMsg
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
import java.time.format.FormatStyle
import java.util.Locale

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

/** Already linked from their own cards, so the feed's principles list leaves them out. */
private val LINKED_PRINCIPLE_URLS = setOf(
	HammerLinks.PRINCIPLE_LOCAL_FIRST,
	HammerLinks.PRINCIPLE_NO_TELEMETRY,
	HammerLinks.PRINCIPLE_DATA_SAFETY,
)

/** Blog posts in this category are linked under the principles instead of listed as news. */
private const val BLOG_PRINCIPLES_CATEGORY = "principles"

private const val LATEST_POST_COUNT = 4

/** A screenshot in a feature tab's slideshow; [name] is the file stem under assets/images/screenshots. */
private class Shot(val name: String, val height: Int, val altKey: String)

/** Each feature tab's slideshow, first slide first. */
private val TAB_SHOTS = mapOf(
	"write" to listOf(
		Shot("editor", 480, "home_feature_editor_alt"),
		Shot("notes", 479, "home_feature_notes_alt"),
		Shot("activity", 479, "home_feature_activity_alt"),
	),
	"world" to listOf(
		Shot("encyclopedia", 479, "home_feature_encyclopedia_alt"),
		Shot("timeline", 479, "home_feature_timeline_alt"),
	),
	"share" to listOf(
		Shot("review", 488, "home_feature_review_alt"),
		Shot("sharing-public", 554, "home_feature_publish_alt"),
		Shot("sharing-private", 539, "home_feature_private_alt"),
		Shot("export", 474, "home_feature_export_alt"),
	),
)
private val POST_DATE_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun Route.homePage(
	configRepository: ConfigRepository,
	markdownRenderer: MarkdownRenderer,
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
			model["principleLocalUrl"] = HammerLinks.PRINCIPLE_LOCAL_FIRST
			model["principleTelemetryUrl"] = HammerLinks.PRINCIPLE_NO_TELEMETRY
			model["principleSafetyUrl"] = HammerLinks.PRINCIPLE_DATA_SAFETY
			model["originStoryUrl"] = HammerLinks.ORIGIN_STORY
			model["tutorialsUrl"] = HammerLinks.TUTORIALS_PLAYLIST
			val locale = call.getLocale()
			model.putAll(blogModel(blogFeedRepository.current(), locale))
			TAB_SHOTS.forEach { (tab, shots) ->
				model["shots_$tab"] = shots.mapIndexed { i, shot ->
					mapOf(
						"name" to shot.name,
						"height" to shot.height,
						"alt" to localizedMsg(locale, shot.altKey),
						"first" to (i == 0),
					)
				}
			}
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

private fun blogModel(feed: BlogFeed?, locale: Locale): Map<String, Any> {
	val (featured, news) = feed?.posts.orEmpty().partition { it.inCategory(BLOG_PRINCIPLES_CATEGORY) }
	val morePrinciples = featured
		.filter { it.url.trimEnd('/') !in LINKED_PRINCIPLE_URLS }
		.map { mapOf("title" to it.title, "url" to it.url) }
	val blogPosts = news.take(LATEST_POST_COUNT).map { post ->
		mapOf(
			"title" to post.title,
			"url" to post.url,
			"date" to post.published?.format(POST_DATE_FORMAT.withLocale(locale)).orEmpty(),
			"isoDate" to post.published?.toLocalDate()?.toString().orEmpty(),
		)
	}

	val model = mutableMapOf<String, Any>(
		"morePrinciples" to morePrinciples,
		"hasMorePrinciples" to morePrinciples.isNotEmpty(),
		"blogPosts" to blogPosts,
		"hasBlogPosts" to blogPosts.isNotEmpty(),
	)
	feed?.siteUrl?.let { model["blogUrl"] = it }
	return model
}
