package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.authenticatedOnly
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.sqliteDateTimeStringToInstant
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.dashboardPage(projectsRepository: ProjectsRepository) {
	authenticatedOnly {
		route("/dashboard") {
			get {
				val session = call.sessions.get<UserSession>()!!

				val projects = projectsRepository.getProjectsWithSyncDate(session.userId)

				val projectsForTemplate = projects.map { project ->
					mapOf(
						"name" to project.name,
						"uuid" to project.uuid,
						"lastSync" to formatSyncDate(project.lastSync)
					)
				}

				val model = call.withDefaults(
					mapOf(
						"username" to session.username,
						"isAdmin" to session.isAdmin,
						"projects" to projectsForTemplate,
						"hasProjects" to projects.isNotEmpty()
					)
				)

				call.respond(MustacheContent("dashboard.mustache", model))
			}
		}
	}
}

private fun formatSyncDate(sqliteDateTime: String): String {
	return try {
		val instant = sqliteDateTimeStringToInstant(sqliteDateTime)
		instant.formatLocal("MMM dd, yyyy 'at' HH:mm")
	} catch (e: Exception) {
		sqliteDateTime
	}
}

private fun kotlin.time.Instant.formatLocal(format: String): String {
	val formatter = java.time.format.DateTimeFormatter.ofPattern(format)
	val zoned = java.time.Instant.ofEpochSecond(epochSeconds).atZone(java.time.ZoneId.systemDefault())
	return formatter.format(zoned)
}
