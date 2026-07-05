package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeaConflictDto
import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeaUploadRequest
import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeasSyncStateResponse
import com.darkrockstudios.apps.hammer.base.http.storyideas.SavedIdeaDto
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.base.http.synchronizer.IdeaConflictException
import com.darkrockstudios.apps.hammer.base.http.synchronizer.IdeaHasher
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

/**
 * Account-level story-idea sync endpoints. All of them run inside the account (projects) sync
 * session and require its syncId.
 */
class ServerIdeasApi(
	httpClient: HttpClient,
	globalSettingsStore: GlobalSettingsStore,
	private val json: Json,
	private val strRes: StrRes,
) : Api(httpClient, globalSettingsStore, strRes) {

	suspend fun getSyncState(syncId: String): Result<IdeasSyncStateResponse> = post(
		path = "/api/ideas/$userId/state",
		parse = { it.body() },
		builder = {
			headers { append(HEADER_SYNC_ID, syncId) }
		},
	)

	suspend fun downloadIdea(id: IdeaId, syncId: String): Result<SavedIdeaDto> = get(
		path = "/api/ideas/$userId/idea/${id.id}",
		parse = { it.body() },
		builder = {
			headers { append(HEADER_SYNC_ID, syncId) }
		},
	)

	/** Throws [IdeaConflictException] (wrapped in `Result.failure`) when the server returns 409. */
	suspend fun uploadIdea(
		idea: StoryIdea,
		originalHash: String?,
		syncId: String,
	): Result<SavedIdeaDto> = post(
		path = "/api/ideas/$userId/idea/${idea.id.id}",
		parse = { it.body() },
		failureHandler = { response ->
			if (response.status == HttpStatusCode.Conflict) {
				val body = json.decodeFromString<IdeaConflictDto>(response.bodyAsText())
				IdeaConflictException(body)
			} else {
				defaultFailureHandler(response, strRes)
			}
		},
	) {
		headers { append(HEADER_SYNC_ID, syncId) }
		contentType(ContentType.Application.Json)
		setBody(
			IdeaUploadRequest(
				idea = idea,
				hash = IdeaHasher.hash(idea),
				originalHash = originalHash,
			)
		)
	}

	suspend fun deleteIdea(id: IdeaId, syncId: String): Result<String> = post(
		path = "/api/ideas/$userId/idea/${id.id}/delete",
		parse = { it.bodyAsText() },
		builder = {
			headers { append(HEADER_SYNC_ID, syncId) }
		},
	)
}
