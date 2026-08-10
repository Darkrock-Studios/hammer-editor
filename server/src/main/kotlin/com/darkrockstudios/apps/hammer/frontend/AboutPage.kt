package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.frontend.utils.metaDescription
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.utilities.MarkdownService
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.aboutPage(
	configRepository: ConfigRepository,
	serverConfig: ServerConfig,
	accountsRepository: AccountsRepository,
	projectAccessRepository: ProjectAccessRepository,
	markdownService: MarkdownService
) {
	route("/about") {
		get {
			val aboutMarkdown = configRepository.get(AdminServerConfig.ABOUT_SERVER)
			if (aboutMarkdown.isBlank()) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			val aboutHtml = markdownService.markdownToSafeHtml(aboutMarkdown)

			val model = call.withDefaults()
			model["page_stylesheet"] = "/assets/css/about.css"
			model["aboutHtml"] = aboutHtml

			// The whitelist half of the band belongs with the signup CTA on the home page.
			val serverMessageHtml = markdownService.markdownToSafeHtml(
				configRepository.get(AdminServerConfig.SERVER_MESSAGE)
			)
			model["serverMessageHtml"] = serverMessageHtml
			model["hasInstanceNotice"] = serverMessageHtml.isNotBlank()
			model["title"] = "${call.msg("about_page_title")} — Hammer"
			metaDescription(aboutMarkdown)?.let { model["metaDescription"] = it }

			populateCommunityCalloutModel(serverConfig, model, accountsRepository, projectAccessRepository)

			call.respond(MustacheContent("about.mustache", model))
		}
	}
}
