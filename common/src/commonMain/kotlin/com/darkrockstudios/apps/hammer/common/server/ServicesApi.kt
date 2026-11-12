package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.SERVICES_ROUTE
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.isSignedIn
import com.darkrockstudios.apps.hammer.common.data.protocolmismatch.ProtocolMismatchRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.url
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import kotlinx.coroutines.CancellationException

/** A service the user's sync server offers, such as LanguageTool, reached as the signed-in user. */
fun interface HammerServiceClient {
	suspend fun request(request: ServiceRequest): ServiceReply
}

class ServiceRequest(
	/** A service name from [com.darkrockstudios.apps.hammer.base.http.HammerService]. */
	val service: String,
	val method: String,
	/** Under the service, such as `/v2/check`. */
	val path: String,
	val headers: List<Pair<String, String>> = emptyList(),
	val body: ByteArray? = null,
)

sealed interface ServiceReply {
	class Response(val status: Int, val body: ByteArray) : ServiceReply

	data class Failed(val reason: Reason, val message: String) : ServiceReply

	enum class Reason {
		/** No sync account on this workspace, or its session has ended. */
		NotSignedIn,

		/** The server doesn't offer the service, or predates services. */
		Unavailable,

		/** The server couldn't be reached. */
		Network,
	}
}

/**
 * Sends [ServiceRequest]s through the sync client, so the bearer token, its refresh, and the
 * protocol header are handled as for sync. The token never leaves this class's [httpClient].
 */
class ServicesApi(
	private val httpClient: HttpClient,
	private val globalSettingsStore: GlobalSettingsStore,
	private val protocolMismatchRepository: ProtocolMismatchRepository,
) : HammerServiceClient {

	override suspend fun request(request: ServiceRequest): ServiceReply {
		val server = globalSettingsStore.serverSettings?.takeIf { it.isSignedIn() }
			?: return ServiceReply.Failed(ServiceReply.Reason.NotSignedIn, "Not signed in to a sync server")
		return try {
			val contentType = request.headers.firstOrNull { it.first.equals(HttpHeaders.ContentType, ignoreCase = true) }
				?.second?.let { runCatching { ContentType.parse(it) }.getOrNull() }
			val response = httpClient.request {
				method = HttpMethod.parse(request.method)
				url(server, "/api/$SERVICES_ROUTE/${server.userId}/${request.service}${request.path}")
				request.headers
					.filterNot { it.first.equals(HttpHeaders.ContentType, ignoreCase = true) }
					.forEach { (name, value) -> header(name, value) }
				// Ktor owns Content-Type, so it travels with the body.
				request.body?.let { setBody(ByteArrayContent(it, contentType)) }
				timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
				// A retried POST would run the check, and spend the user's limit, twice.
				retry { noRetry() }
			}
			when (response.status) {
				// The client already tried refreshing the token, so the session is gone.
				HttpStatusCode.Unauthorized ->
					ServiceReply.Failed(ServiceReply.Reason.NotSignedIn, "Sign in to your sync server again")

				HttpStatusCode.NotFound, HttpStatusCode.ServiceUnavailable ->
					ServiceReply.Failed(ServiceReply.Reason.Unavailable, "The sync server doesn't offer ${request.service}")

				HttpStatusCode.UpgradeRequired -> {
					protocolMismatchRepository.notifyMismatch(
						clientProtocolVersion = HAMMER_PROTOCOL_VERSION,
						serverProtocolVersion = response.headers[HAMMER_PROTOCOL_HEADER]?.toIntOrNull(),
					)
					ServiceReply.Failed(ServiceReply.Reason.Unavailable, "The sync server needs a different version of Hammer")
				}

				else -> ServiceReply.Response(response.status.value, response.bodyAsBytes())
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Napier.w("Service ${request.service} request failed: ${e::class.simpleName}")
			ServiceReply.Failed(ServiceReply.Reason.Network, e.message ?: "Couldn't reach the sync server")
		}
	}

	companion object {
		const val REQUEST_TIMEOUT_MS = 30_000L
	}
}
