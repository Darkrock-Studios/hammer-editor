package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.HEADER_ENTITY_HASH
import com.darkrockstudios.apps.hammer.base.http.HEADER_ENTITY_TYPE
import com.darkrockstudios.apps.hammer.base.http.HEADER_ORIGINAL_HASH
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.base.http.SaveEntityResponse
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestDataSet1
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.utils.io.core.toByteArray
import korlibs.io.compression.compress
import korlibs.io.compression.deflate.GZIP
import kotlinx.serialization.json.Json
import org.junit.Before
import kotlin.test.assertEquals

abstract class ProjectSyncTestBase : EndToEndTest() {

	lateinit var json: Json

	@Before
	override fun setup() {
		super.setup()

		json = createJsonSerializer()
	}

	protected suspend fun HttpClient.projectSynchronizationBegan(
		userId: Long,
		authToken: Token,
		clientState: ClientEntityState
	): ProjectSynchronizationBegan {
		val beginSyncResponse = projectSynchronizationBeganRequest(userId, authToken, clientState)

		assertEquals(HttpStatusCode.OK, beginSyncResponse.status)
		val synchronizationBegan = beginSyncResponse.body<ProjectSynchronizationBegan>()
		return synchronizationBegan
	}

	protected suspend fun HttpClient.projectSynchronizationBeganRequest(
		userId: Long,
		authToken: Token,
		clientState: ClientEntityState
	): HttpResponse {
		val beginSyncResponse =
			post(api("project/$userId/${TestDataSet1.project1.name}/begin_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
				}
				contentType(ContentType.Application.OctetStream)
				parameter("projectId", TestDataSet1.project1.uuid.toString())

				val stateJson = json.encodeToString(ClientEntityState.serializer(), clientState)
				val compressed = stateJson.toByteArray().compress(GZIP)

				setBody(compressed)
			}

		return beginSyncResponse
	}

	protected suspend inline fun <reified T : ApiProjectEntity> HttpClient.uploadEntityRequest(
		userId: Long,
		authToken: Token,
		syncId: String,
		entity: T,
		originalhash: String,
		force: Boolean = false,
	): SaveEntityResponse {
		val uploadResponse =
			post(api("project/$userId/${TestDataSet1.project1.name}/upload_entity/${entity.id}")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId)
					append(HEADER_ORIGINAL_HASH, originalhash)
					append(HEADER_ENTITY_TYPE, entity.type.toString())
				}
				contentType(ContentType.Application.Json)
				url {
					parameters.append("projectId", TestDataSet1.project1.uuid.toString())
					parameters.append("force", force.toString())
				}

				setBody(entity)
			}
		assertEquals(HttpStatusCode.OK, uploadResponse.status)

		val savedEntity = uploadResponse.body<SaveEntityResponse>()
		return savedEntity
	}

	protected suspend inline fun <reified T : ApiProjectEntity> HttpClient.uploadConflictedEntityRequest(
		userId: Long,
		authToken: Token,
		syncId: String,
		entity: T,
		originalhash: String,
		force: Boolean = false,
	): T {
		val uploadResponse =
			post(api("project/$userId/${TestDataSet1.project1.name}/upload_entity/${entity.id}")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId)
					append(HEADER_ORIGINAL_HASH, originalhash)
					append(HEADER_ENTITY_TYPE, entity.type.toString())
				}
				contentType(ContentType.Application.Json)
				url {
					parameters.append("projectId", TestDataSet1.project1.uuid.toString())
					parameters.append("force", force.toString())
				}

				setBody(entity)
			}
		assertEquals(HttpStatusCode.Conflict, uploadResponse.status)

		val serverEntity = uploadResponse.body<T>()
		return serverEntity
	}

	protected suspend inline fun <reified T : ApiProjectEntity> HttpClient.downloadEntity(
		userId: Long,
		authToken: Token,
		syncId: String,
		entityId: Int,
		entityHash: String?,
	): T {
		// End Sync
		val downloadResponse =
			downloadEntityRequest(userId, authToken, syncId, entityId, entityHash)
		assertEquals(HttpStatusCode.OK, downloadResponse.status)

		val downloadedEntity: T = downloadResponse.body<T>()
		return downloadedEntity
	}

	protected suspend inline fun HttpClient.downloadEntityRequest(
		userId: Long,
		authToken: Token,
		syncId: String,
		entityId: Int,
		entityHash: String?,
	): HttpResponse {
		// End Sync
		val downloadResponse =
			get(api("project/$userId/${TestDataSet1.project1.name}/download_entity/${entityId}")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId)
					if (entityHash != null) {
						append(HEADER_ENTITY_HASH, entityHash)
					}
				}
				contentType(ContentType.Application.FormUrlEncoded)
				parameter("projectId", TestDataSet1.project1.uuid.toString())
			}
		return downloadResponse
	}

	protected suspend fun HttpClient.endSyncRequest(
		userId: Long,
		authToken: Token,
		synchronizationBegan: ProjectSynchronizationBegan
	) {
		// End Sync
		val endSyncResponse =
			get(api("project/$userId/${TestDataSet1.project1.name}/end_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, synchronizationBegan.syncId)
				}
				contentType(ContentType.Application.FormUrlEncoded)
				parameter("projectId", TestDataSet1.project1.uuid.toString())

				setBody(
					FormDataContent(
						Parameters.build {
							append("lastSync", synchronizationBegan.lastSync.toString())
							append("lastId", synchronizationBegan.lastId.toString())
						}
					)
				)
			}
		assertEquals(HttpStatusCode.OK, endSyncResponse.status)
	}
}