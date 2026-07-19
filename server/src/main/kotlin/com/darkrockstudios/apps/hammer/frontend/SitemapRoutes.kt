package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.database.CommunityAuthor
import com.darkrockstudios.apps.hammer.database.CommunityFeedStory
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.frontend.utils.publicBaseUrl
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** One `<url>` in a sitemap urlset. [lastmod] is a preformatted W3C datetime, or null. */
internal data class SitemapEntry(val loc: String, val lastmod: String? = null)

/** The sitemap protocol caps a single file at 50,000 URLs / 50 MB. */
private const val SITEMAP_MAX_URLS = 50_000
private const val SITEMAP_FETCH_PAGE = 500

/**
 * Serves the site's sitemaps:
 *  - `/sitemap.xml`         — a sitemap index over the content sitemap below.
 *  - `/sitemap-content.xml` — a urlset of this server's own public pages: the enabled static
 *                             marketing pages, plus community authors and published stories
 *                             enumerated from the database at request time.
 *
 * Both require [ServerConfig.publicUrl] so `<loc>`s are absolute; without it they 404. The
 * client-controlled request Host is deliberately not used as a fallback (see [publicBaseUrl]).
 */
fun Route.sitemapRoutes(
	serverConfig: ServerConfig,
	accountsRepository: AccountsRepository,
	projectAccessRepository: ProjectAccessRepository,
	configRepository: ConfigRepository,
) {
	get("/sitemap.xml") {
		val base = call.publicBaseUrl() ?: run {
			call.respond(HttpStatusCode.NotFound)
			return@get
		}
		val children = buildList {
			add("$base/sitemap-content.xml")
			addAll(serverConfig.additionalSitemaps.map(String::trim).filter(::isHttpUrl))
		}
		call.respondText(buildSitemapIndexXml(children), ContentType.Application.Xml)
	}

	get("/sitemap-content.xml") {
		val base = call.publicBaseUrl() ?: run {
			call.respond(HttpStatusCode.NotFound)
			return@get
		}

		val authors: List<CommunityAuthor>
		val stories: List<CommunityFeedStory>
		if (serverConfig.communityEnabled) {
			authors = fetchAllCommunityAuthors(accountsRepository)
			stories = fetchAllFeedStories(projectAccessRepository)
		} else {
			authors = emptyList()
			stories = emptyList()
		}

		val entries = buildContentEntries(
			baseUrl = base,
			includeAbout = configRepository.get(AdminServerConfig.ABOUT_SERVER).isNotBlank(),
			includeTerms = serverConfig.termsOfService != null,
			includePrivacy = serverConfig.privacyPolicy != null,
			communityEnabled = serverConfig.communityEnabled,
			authors = authors,
			stories = stories,
		)
		call.respondText(buildSitemapUrlsetXml(entries), ContentType.Application.Xml)
	}
}

internal fun buildContentEntries(
	baseUrl: String,
	includeAbout: Boolean,
	includeTerms: Boolean,
	includePrivacy: Boolean,
	communityEnabled: Boolean,
	authors: List<CommunityAuthor>,
	stories: List<CommunityFeedStory>,
): List<SitemapEntry> {
	val base = baseUrl.trimEnd('/')
	val entries = mutableListOf<SitemapEntry>()
	entries += SitemapEntry("$base/")
	if (includeAbout) entries += SitemapEntry("$base/about")
	if (includeTerms) entries += SitemapEntry("$base/terms")
	if (includePrivacy) entries += SitemapEntry("$base/privacy")
	if (communityEnabled) {
		entries += SitemapEntry("$base/community/feed")
		entries += SitemapEntry("$base/community/authors")
	}
	for (author in authors) {
		entries += SitemapEntry("$base/a/${ProjectName.penNameForUrl(author.penName)}")
	}
	for (story in stories) {
		val pen = ProjectName.penNameForUrl(story.penName)
		val segment = ProjectName.projectSegment(story.projectName, story.projectUuid)
		entries += SitemapEntry("$base/a/$pen/$segment", story.publishedAt.toString())
	}
	return if (entries.size > SITEMAP_MAX_URLS) entries.take(SITEMAP_MAX_URLS) else entries
}

internal fun buildSitemapIndexXml(sitemapLocs: List<String>): String {
	val sb = StringBuilder()
	sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
	sb.appendLine("""<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""")
	for (loc in sitemapLocs) {
		sb.appendLine("\t<sitemap><loc>${xmlEscape(loc)}</loc></sitemap>")
	}
	sb.appendLine("</sitemapindex>")
	return sb.toString()
}

internal fun buildSitemapUrlsetXml(entries: List<SitemapEntry>): String {
	val sb = StringBuilder()
	sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
	sb.appendLine("""<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""")
	for (entry in entries) {
		sb.append("\t<url><loc>").append(xmlEscape(entry.loc)).append("</loc>")
		if (entry.lastmod != null) {
			sb.append("<lastmod>").append(xmlEscape(entry.lastmod)).append("</lastmod>")
		}
		sb.appendLine("</url>")
	}
	sb.appendLine("</urlset>")
	return sb.toString()
}

private fun isHttpUrl(value: String): Boolean =
	value.startsWith("http://") || value.startsWith("https://")

private fun xmlEscape(value: String): String = buildString {
	for (c in value) when (c) {
		'&' -> append("&amp;")
		'<' -> append("&lt;")
		'>' -> append("&gt;")
		'"' -> append("&quot;")
		'\'' -> append("&apos;")
		else -> append(c)
	}
}

private suspend fun fetchAllCommunityAuthors(repo: AccountsRepository): List<CommunityAuthor> {
	val total = repo.countCommunityAuthors()
	val out = ArrayList<CommunityAuthor>()
	var page = 0
	while (out.size < total && out.size < SITEMAP_MAX_URLS) {
		val batch = repo.getCommunityAuthors(page, SITEMAP_FETCH_PAGE)
		if (batch.isEmpty()) break
		out += batch
		page++
	}
	return out
}

private suspend fun fetchAllFeedStories(repo: ProjectAccessRepository): List<CommunityFeedStory> {
	val total = repo.countCommunityFeedStories()
	val out = ArrayList<CommunityFeedStory>()
	var page = 0
	while (out.size < total && out.size < SITEMAP_MAX_URLS) {
		val batch = repo.getCommunityFeedStories(page, SITEMAP_FETCH_PAGE)
		if (batch.isEmpty()) break
		out += batch
		page++
	}
	return out
}
