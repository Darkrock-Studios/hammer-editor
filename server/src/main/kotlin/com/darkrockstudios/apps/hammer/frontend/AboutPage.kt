package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

fun Route.aboutPage(
	configRepository: ConfigRepository,
	serverConfig: ServerConfig,
	accountsRepository: AccountsRepository,
	projectAccessRepository: ProjectAccessRepository
) {
	val markdownFlavour = CommonMarkFlavourDescriptor()
	val markdownParser = MarkdownParser(markdownFlavour)

	route("/about") {
		get {
			val aboutMarkdown = configRepository.get(AdminServerConfig.ABOUT_SERVER)
			if (aboutMarkdown.isBlank()) {
				call.respond(HttpStatusCode.NotFound)
				return@get
			}

			val parsedTree = markdownParser.buildMarkdownTreeFromString(aboutMarkdown)
			val aboutHtml = HtmlGenerator(aboutMarkdown, parsedTree, markdownFlavour).generateHtml()

			val model = call.withDefaults()
			model["page_stylesheet"] = "/assets/css/about.css"
			model["aboutHtml"] = aboutHtml

			populateCommunityCalloutModel(serverConfig, model, accountsRepository, projectAccessRepository)

			call.respond(MustacheContent("about.mustache", model))
		}
	}
}
