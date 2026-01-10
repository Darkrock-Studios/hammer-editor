package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.communityPage(
	projectAccessRepository: ProjectAccessRepository,
	accountsRepository: AccountsRepository,
	serverConfig: ServerConfig,
) {
	route("/community") {
		authorsPage(accountsRepository, serverConfig)
		feedPage(projectAccessRepository, serverConfig)

		get { call.respondRedirect("/community/feed") }
	}
}