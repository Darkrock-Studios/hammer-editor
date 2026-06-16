package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.projects.ProjectWithSyncDate
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import java.net.URLDecoder
import java.net.URLEncoder

object ProjectName {
	/**
	 * Decode a URL segment: URL decode then replace dashes with spaces.
	 * Example: "My-Story-Name" -> "My Story Name"
	 *
	 * Lossy — every dash becomes a space, so a literal-dash name can't be recovered. Use
	 * [findProjectByUrlName] to resolve a project from a URL segment.
	 */
	fun decodeFromUrl(urlSegment: String): String =
		URLDecoder.decode(urlSegment, "UTF-8").replace('-', ' ')

	/**
	 * Format a string for use in a URL: replace spaces with dashes, then URL encode.
	 * Example: "My Story Name" -> "My-Story-Name"
	 */
	fun formatForUrl(name: String): String =
		URLEncoder.encode(name.replace(' ', '-'), "UTF-8")
}

/**
 * Resolves a project from a URL segment made by [ProjectName.formatForUrl]. Tries the exact decoded
 * name first, then matches the segment against each project re-encoded with [ProjectName.formatForUrl]
 * — lossless even when the name contains dashes the decode can't reverse.
 */
suspend fun ProjectsRepository.findProjectByUrlName(
	userId: Long,
	urlName: String,
): ProjectWithSyncDate? {
	getProjectByName(userId, ProjectName.decodeFromUrl(urlName))?.let { return it }
	return getProjectsWithSyncDate(userId).find { ProjectName.formatForUrl(it.name) == urlName }
}