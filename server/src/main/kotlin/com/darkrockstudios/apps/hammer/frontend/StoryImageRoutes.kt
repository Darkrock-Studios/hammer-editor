package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.frontend.utils.applyRevalidationHeaders
import com.darkrockstudios.apps.hammer.frontend.utils.matchesETag
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ServerProjectDataRepository
import com.darkrockstudios.apps.hammer.story.StoryImageService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes

/**
 * An image file opened directly is a document on our origin, and an SVG one can carry script.
 * Sandboxed with nothing allowed but its own inline styles, it renders and does nothing else.
 */
internal const val STORY_IMAGE_CSP = "default-src 'none'; style-src 'unsafe-inline'; sandbox"

/**
 * Serves Image entity [imageId] of [projectDef] to a reader the caller has already let in. Every
 * failure is a 404, so a response never tells whether an image exists.
 *
 * The stored hash is the validator: a reader holding it is answered 304 without decrypting.
 */
internal suspend fun ApplicationCall.respondStoryImage(
	storyImageService: StoryImageService,
	userId: Long,
	projectDef: ProjectDefinition,
	imageId: Int,
) {
	val hash = storyImageService.imageHash(userId, projectDef, imageId)
	if (hash == null) {
		respond(HttpStatusCode.NotFound)
		return
	}

	val etag = "W/\"$hash\""
	if (matchesETag(etag)) {
		applyImageHeaders(etag)
		respond(HttpStatusCode.NotModified)
		return
	}

	val image = storyImageService.loadImage(userId, projectDef, imageId)
	if (image == null) {
		respond(HttpStatusCode.NotFound)
		return
	}

	applyImageHeaders(etag)
	respondBytes(image.bytes, ContentType.parse(image.mediaType))
}

/**
 * The cover's Image entity id when the server holds that image. A cover can name an image that has
 * not synced yet or was deleted on another device, and that must show no cover rather than a broken one.
 */
internal suspend fun storyCoverImageId(
	serverProjectDataRepository: ServerProjectDataRepository,
	storyImageService: StoryImageService,
	userId: Long,
	projectDef: ProjectDefinition,
): Int? {
	val coverId = serverProjectDataRepository.loadCoverImageId(userId, projectDef) ?: return null
	return coverId.takeIf { storyImageService.imageHash(userId, projectDef, it) != null }
}

private fun ApplicationCall.applyImageHeaders(etag: String) {
	applyRevalidationHeaders(etag)
	// DefaultHeaders only adds a header the response lacks, so this replaces the site-wide policy.
	response.header("Content-Security-Policy", STORY_IMAGE_CSP)
	response.header(HttpHeaders.ContentDisposition, "inline")
}
