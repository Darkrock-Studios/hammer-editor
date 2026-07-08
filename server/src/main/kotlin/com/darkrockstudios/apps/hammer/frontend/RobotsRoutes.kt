package com.darkrockstudios.apps.hammer.frontend

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Well-known AI/LLM training & data-harvesting crawler user-agent tokens.
 *
 * Sourced from the community-maintained https://github.com/ai-robots-txt/ai.robots.txt
 * list (snapshot; see also https://www.fastly.com/blog/teach-your-robots-txt-a-new-trick-for-ai).
 * This is a point-in-time copy — new crawlers appear constantly, so it should be
 * refreshed periodically. Blocking is best-effort: a well-behaved crawler honours it,
 * a hostile one ignores it.
 *
 * We deliberately DO NOT list general search crawlers (Googlebot, Bingbot, …) here so
 * that public pages and publicly-published community stories remain indexable for search.
 * `Google-Extended` and `Applebot-Extended` are the AI-training opt-out tokens for
 * Google/Apple: listing them blocks AI training while Googlebot/Applebot keep crawling
 * for search results.
 *
 * We also deliberately omit link-preview fetchers (e.g. facebookexternalhit, Twitterbot)
 * so that sharing a story link on social media still renders a preview card.
 */
internal val AI_CRAWLER_USER_AGENTS: List<String> = listOf(
	"AI2Bot",
	"Ai2Bot-Dolma",
	"Amazonbot",
	"anthropic-ai",
	"Applebot-Extended",
	"Bytespider",
	"CCBot",
	"ChatGPT-User",
	"Claude-Web",
	"ClaudeBot",
	"cohere-ai",
	"cohere-training-data-crawler",
	"Diffbot",
	"DuckAssistBot",
	"FacebookBot",
	"FriendlyCrawler",
	"Google-Extended",
	"GPTBot",
	"iaskspider/2.0",
	"ICC-Crawler",
	"ImagesiftBot",
	"img2dataset",
	"ISSCyberRiskCrawler",
	"Kangaroo Bot",
	"Meta-ExternalAgent",
	"Meta-ExternalFetcher",
	"OAI-SearchBot",
	"omgili",
	"omgilibot",
	"PanguBot",
	"Perplexity-User",
	"PerplexityBot",
	"PetalBot",
	"Sidetrade indexer bot",
	"Timpibot",
	"VelenPublicWebCrawler",
	"Webzio-Extended",
	"YouBot",
)

/**
 * App/functional areas that should never be indexed by any crawler. These are either
 * authenticated surfaces, API endpoints, or one-off flows with no useful public content.
 */
internal val DISALLOWED_PATHS: List<String> = listOf(
	"/api/",
	"/admin",
	"/dashboard",
	"/login",
	"/logout",
	"/setup",
	"/account",
	"/reset-password",
	"/forgot-password",
	"/review/",
)

/**
 * Builds the `robots.txt` body.
 *
 * Two groups:
 *  - `User-agent: *` (search engines): everything is crawlable except the app/private
 *    paths in [DISALLOWED_PATHS]. Story pages under `/a/` stay crawlable so that
 *    publicly-published community stories can be indexed; non-indexable stories emit a
 *    per-page `X-Robots-Tag: noindex` header instead (robots.txt can't tell them apart
 *    by path).
 *  - The AI-training crawlers in [AI_CRAWLER_USER_AGENTS]: `Disallow: /` (opt out entirely).
 *
 * @param sitemapUrl absolute URL of the sitemap, appended as a `Sitemap:` line when set.
 */
internal fun buildRobotsTxt(sitemapUrl: String? = null): String {
	val sb = StringBuilder()

	sb.appendLine("# Search engines: index public pages and publicly-published community")
	sb.appendLine("# stories, but keep authenticated and functional areas out.")
	sb.appendLine("User-agent: *")
	for (path in DISALLOWED_PATHS) {
		sb.appendLine("Disallow: $path")
	}
	sb.appendLine("Allow: /")
	sb.appendLine()

	sb.appendLine("# AI training / data-harvesting crawlers: opt out entirely.")
	for (agent in AI_CRAWLER_USER_AGENTS) {
		sb.appendLine("User-agent: $agent")
	}
	sb.appendLine("Disallow: /")

	if (!sitemapUrl.isNullOrBlank()) {
		sb.appendLine()
		sb.appendLine("Sitemap: $sitemapUrl")
	}

	return sb.toString()
}

fun Route.robotsRoutes() {
	get("/robots.txt") {
		call.respondText(buildRobotsTxt(), ContentType.Text.Plain)
	}
}

/**
 * Directive sent to well-behaved crawlers to keep a specific page out of their index.
 * Used on story/author pages, where indexability depends on per-record state
 * (community membership, public vs. password-protected) that robots.txt path rules
 * can't express.
 */
private const val ROBOTS_NOINDEX = "noindex, nofollow"

/**
 * Sets `X-Robots-Tag: noindex, nofollow` when [indexable] is false. When true, no header
 * is added so the page is indexable by default. Must be called before the response body
 * is sent.
 */
fun ApplicationCall.applyRobotsTag(indexable: Boolean) {
	if (!indexable) {
		response.header("X-Robots-Tag", ROBOTS_NOINDEX)
	}
}
