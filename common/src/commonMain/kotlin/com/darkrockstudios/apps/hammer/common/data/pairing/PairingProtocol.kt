package com.darkrockstudios.apps.hammer.common.data.pairing

import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The phone to watch pairing handshake, carried as Wearable Data Layer messages. The watch sends a
 * [PairRequest] to a phone advertising [PHONE_CAPABILITY]; the phone answers with a [PairResponse]
 * holding a session minted for the watch's own install.
 */
object PairingProtocol {
	const val PHONE_CAPABILITY = "hammer_phone_pairing"
	const val PATH_PREFIX = "/hammer/pair"
	const val REQUEST_PATH = "$PATH_PREFIX/request"
	const val RESPONSE_PATH = "$PATH_PREFIX/response"
	const val VERSION = 1

	private val json = Json {
		ignoreUnknownKeys = true
		classDiscriminator = "type"
	}

	fun encodeRequest(request: PairRequest): ByteArray =
		json.encodeToString(PairRequest.serializer(), request).encodeToByteArray()

	/** Null when the payload is not a request this version understands. */
	fun decodeRequest(payload: ByteArray): PairRequest? = try {
		json.decodeFromString(PairRequest.serializer(), payload.decodeToString())
	} catch (_: IllegalArgumentException) {
		null
	}

	fun encodeResponse(response: PairResponse): ByteArray =
		json.encodeToString(PairResponse.serializer(), response).encodeToByteArray()

	/** Null when the payload is not a response this version understands. */
	fun decodeResponse(payload: ByteArray): PairResponse? = try {
		json.decodeFromString(PairResponse.serializer(), payload.decodeToString())
	} catch (_: IllegalArgumentException) {
		null
	}
}

@Serializable
data class PairRequest(
	val requestId: String,
	val installId: String,
	val deviceLabel: String,
	val version: Int = PairingProtocol.VERSION,
)

@Serializable
sealed interface PairResponse {
	val requestId: String

	@Serializable
	@SerialName("success")
	data class Success(
		override val requestId: String,
		val settings: ServerSettings,
	) : PairResponse

	@Serializable
	@SerialName("error")
	data class Error(
		override val requestId: String,
		val code: PairErrorCode,
		val message: String? = null,
	) : PairResponse
}

@Serializable
enum class PairErrorCode {
	Declined,
	NotSignedIn,
	ServerRejected,
	Unsupported,
}
