package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingActivityResponse
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Client API for the project's writing-activity sync endpoints. The server
 * is dumb storage — it holds opaque per-device blobs keyed by
 * `(project, deviceId)` and never inspects the contents. All merge logic
 * lives in [com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.WritingActivitySyncOperation].
 */
class WritingActivityApi(
	httpClient: HttpClient,
	globalSettingsStore: GlobalSettingsStore,
	private val strRes: StrRes,
) : Api(httpClient, globalSettingsStore, strRes) {

	/** Fetch every device's slot for this project. */
	suspend fun getWritingActivity(
		userId: Long,
		projectName: String,
		projectId: ProjectId,
	): Result<WritingActivityResponse> = get(
		path = "/api/project/$userId/$projectName/writing_activity",
		parse = { it.body() },
	) {
		url {
			parameters.append("projectId", projectId.id)
		}
	}

	/** Replace this device's slot on the server. The server stores it as-is. */
	suspend fun uploadDeviceLog(
		userId: Long,
		projectName: String,
		projectId: ProjectId,
		deviceId: String,
		log: DeviceLog,
	): Result<String> = post(
		path = "/api/project/$userId/$projectName/writing_activity/$deviceId",
	) {
		contentType(ContentType.Application.Json)
		url {
			parameters.append("projectId", projectId.id)
		}
		setBody(log)
	}
}
