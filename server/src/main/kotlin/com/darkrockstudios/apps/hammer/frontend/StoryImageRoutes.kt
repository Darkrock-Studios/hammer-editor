package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.frontend.utils.applyRevalidationHeaders
import com.darkrockstudios.apps.hammer.frontend.utils.matchesETag
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.utilities.ServerResult
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
	projectEntityDatasource: ProjectEntityDatasource,
	userId: Long,
	projectDef: ProjectDefinition,
	imageId: Int,
) {
	// The hash lookup is not typed, and the id of a scene must not answer as an image.
	if (projectEntityDatasource.findEntityType(imageId, userId, projectDef) != ApiProjectEntity.Type.IMAGE) {
		respond(HttpStatusCode.NotFound)
		return
	}
	val hash = (projectEntityDatasource.loadEntityHash(userId, projectDef, imageId) as? ServerResult.Success)?.data
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

	val image = (projectEntityDatasource.loadEntity(
		userId = userId,
		projectDef = projectDef,
		entityId = imageId,
		entityType = ApiProjectEntity.Type.IMAGE,
		serializer = ApiProjectEntity.ImageEntity.serializer(),
	) as? ServerResult.Success)?.data
	val mimeType = image?.let { ImageFormat.mimeType(it.fileExtension) }
	val bytes = image?.let { ImageFormat.decodeBase64(it.base64) }
	if (mimeType == null || bytes == null) {
		respond(HttpStatusCode.NotFound)
		return
	}

	applyImageHeaders(etag)
	respondBytes(bytes, ContentType.parse(mimeType))
}

private fun ApplicationCall.applyImageHeaders(etag: String) {
	applyRevalidationHeaders(etag)
	// DefaultHeaders only adds a header the response lacks, so this replaces the site-wide policy.
	response.header("Content-Security-Policy", STORY_IMAGE_CSP)
	response.header(HttpHeaders.ContentDisposition, "inline")
}
