package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.math.ceil

fun Route.authorsPage(
	accountsRepository: AccountsRepository,
	serverConfig: ServerConfig
) {
	route("/authors") {
		get {
			// Only accessible if community is enabled
			if (!serverConfig.communityEnabled) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			val pageSize = 12
			val queryPage = call.request.queryParameters["page"]?.toIntOrNull() ?: 0

			val totalCount = accountsRepository.countCommunityAuthors()
			val totalPages = ceil(totalCount.toDouble() / pageSize).toInt()
			val currentPage = if (totalPages > 0) queryPage.coerceIn(0, totalPages - 1) else 0

			val authors = accountsRepository.getCommunityAuthors(currentPage, pageSize)

			val authorsForTemplate = authors.map { author ->
				mapOf(
					"penName" to author.penName,
					"penNameUrl" to ProjectName.penNameForUrl(author.penName),
					"hasBio" to !author.bio.isNullOrBlank(),
					"bioPreview" to author.bio?.take(150)?.let { if (author.bio.length > 150) "$it..." else it }
				)
			}

			val model = call.withDefaults(
				mapOf(
					"page_stylesheet" to "/assets/css/authors.css",
					"authorsActive" to true,
					"authors" to authorsForTemplate,
					"hasAuthors" to authors.isNotEmpty(),
					"authorCount" to totalCount,
					"currentPage" to currentPage,
					"currentPageDisplay" to currentPage + 1,
					"totalPages" to totalPages,
					"hasNextPage" to (currentPage < (totalPages - 1)),
					"hasPrevPage" to (currentPage > 0),
					"nextPage" to currentPage + 1,
					"prevPage" to currentPage - 1,
					"isPaged" to (totalPages > 1)
				)
			)

			call.respond(MustacheContent("authors.mustache", model))
		}
	}
}
