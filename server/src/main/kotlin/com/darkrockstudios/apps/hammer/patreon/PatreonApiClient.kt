package com.darkrockstudios.apps.hammer.patreon

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Serializable
data class PatreonMemberAttributes(
	val email: String? = null,
	val patron_status: String? = null,
	val currently_entitled_amount_cents: Int = 0
)

@Serializable
data class PatreonMemberData(
	val id: String,
	val attributes: PatreonMemberAttributes? = null
)

@Serializable
data class PatreonPaginationCursors(
	val next: String? = null
)

@Serializable
data class PatreonPagination(
	val cursors: PatreonPaginationCursors? = null
)

@Serializable
data class PatreonMeta(
	val pagination: PatreonPagination? = null
)

@Serializable
data class PatreonMembersResponse(
	val data: List<PatreonMemberData> = emptyList(),
	val meta: PatreonMeta? = null
)

@Serializable
data class PatreonCampaignData(
	val id: String,
	val type: String
)

@Serializable
data class PatreonCampaignsResponse(
	val data: List<PatreonCampaignData> = emptyList()
)

class PatreonApiClient(
	private val logger: Logger
) {
	private val json = Json {
		ignoreUnknownKeys = true
		isLenient = true
	}

	private val httpClient = HttpClient(Java) {
		install(ContentNegotiation) {
			json(json)
		}
		install(Logging) {
			level = LogLevel.INFO
		}
	}

	suspend fun fetchAllQualifyingMembers(
		campaignId: String,
		accessToken: String,
		minimumAmountCents: Int
	): Result<List<String>> {
		val allEmails = mutableListOf<String>()
		var cursor: String? = null

		try {
			do {
				val response = fetchMembersPage(campaignId, accessToken, cursor)

				response.data.forEach { member ->
					val attributes = member.attributes
					if (attributes != null &&
						attributes.patron_status == "active_patron" &&
						attributes.currently_entitled_amount_cents >= minimumAmountCents &&
						!attributes.email.isNullOrBlank()
					) {
						allEmails.add(attributes.email)
					}
				}

				cursor = response.meta?.pagination?.cursors?.next
			} while (cursor != null)

			return Result.success(allEmails)
			// API boundary: any failure becomes a failed Result.
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			logger.error("Failed to fetch Patreon members", e)
			return Result.failure(e)
		}
	}

	private suspend fun fetchMembersPage(
		campaignId: String,
		accessToken: String,
		cursor: String? = null
	): PatreonMembersResponse {
		val url = buildString {
			append("https://www.patreon.com/api/oauth2/v2/campaigns/$campaignId/members")
			append("?fields[member]=email,patron_status,currently_entitled_amount_cents")
			append("&page[count]=1000")
			if (cursor != null) {
				append("&page[cursor]=$cursor")
			}
		}

		val response = httpClient.get(url) {
			header(HttpHeaders.Authorization, "Bearer $accessToken")
			header(HttpHeaders.UserAgent, "HammerServer/1.0")
		}

		if (!response.status.isSuccess()) {
			throw PatreonApiException("Patreon API returned ${response.status}")
		}

		return response.body()
	}

	suspend fun fetchCampaignId(accessToken: String): Result<String> {
		return try {
			val url = "https://www.patreon.com/api/oauth2/v2/campaigns"

			val response = httpClient.get(url) {
				header(HttpHeaders.Authorization, "Bearer $accessToken")
				header(HttpHeaders.UserAgent, "HammerServer/1.0")
			}

			if (!response.status.isSuccess()) {
				return Result.failure(PatreonApiException("Patreon API returned ${response.status}"))
			}

			val campaignsResponse: PatreonCampaignsResponse = response.body()

			if (campaignsResponse.data.isEmpty()) {
				return Result.failure(PatreonApiException("No campaigns found for this account"))
			}

			// Return the first campaign ID
			Result.success(campaignsResponse.data.first().id)
		} catch (e: Exception) {
			logger.error("Failed to fetch campaign ID", e)
			Result.failure(e)
		}
	}

	fun verifyWebhookSignature(payload: String, signature: String, secret: String): Boolean {
		return try {
			val mac = Mac.getInstance("HmacMD5")
			val secretKey = SecretKeySpec(secret.toByteArray(), "HmacMD5")
			mac.init(secretKey)
			val hash = mac.doFinal(payload.toByteArray())
			val computedSignature = hash.joinToString("") { "%02x".format(it) }
			computedSignature.equals(signature, ignoreCase = true)
		} catch (e: Exception) {
			logger.error("Failed to verify webhook signature", e)
			false
		}
	}

	fun close() {
		httpClient.close()
	}
}

class PatreonApiException(message: String) : Exception(message)
