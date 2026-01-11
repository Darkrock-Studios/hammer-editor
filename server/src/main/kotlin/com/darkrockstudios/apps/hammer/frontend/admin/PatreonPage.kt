package com.darkrockstudios.apps.hammer.frontend.admin

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.frontend.utils.formatPatreonDate
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.respondHtmlFragment
import com.darkrockstudios.apps.hammer.frontend.withDefaults
import com.darkrockstudios.apps.hammer.patreon.PatreonApiClient
import com.darkrockstudios.apps.hammer.patreon.PatreonSyncService
import io.ktor.htmx.*
import io.ktor.http.*
import io.ktor.server.htmx.*
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import org.koin.ktor.ext.inject

internal fun Route.patreonSettingsRoutes(
	configRepository: ConfigRepository,
	patreonSyncService: PatreonSyncService,
	serverConfig: ServerConfig
) {
	val patreonApiClient: PatreonApiClient by inject()

	route("/patreon") {
		// POST /admin/patreon/fetch-campaign - Fetch Campaign ID from Patreon API
		hx.post("/fetch-campaign") {
			val params = call.receiveParameters()
			val accessToken = params["accessToken"]?.trim().orEmpty()

			val currentConfig = configRepository.get(AdminServerConfig.PATREON_CONFIG)
			val effectiveToken = accessToken.ifEmpty { currentConfig.creatorAccessToken }

			if (effectiveToken.isEmpty()) {
				val errorMsg = call.msg("admin_patreon_error_token_required")
				call.respondHtmlFragment {
					input {
						id = "campaignId"
						name = "campaignId"
						type = InputType.text
						value = ""
						classes = setOf("form-input", "form-input--error")
						placeholder = errorMsg
					}
				}
				return@post
			}

			val result = patreonApiClient.fetchCampaignId(effectiveToken)

			if (result.isSuccess) {
				val campaignId = result.getOrThrow()
				call.respondHtmlFragment {
					input {
						id = "campaignId"
						name = "campaignId"
						type = InputType.text
						value = campaignId
						classes = setOf("form-input")
						placeholder = "123456"
					}
				}
			} else {
				val errorMsg = call.msg("admin_patreon_error_fetch_failed")
				call.respondHtmlFragment {
					input {
						id = "campaignId"
						name = "campaignId"
						type = InputType.text
						value = ""
						classes = setOf("form-input", "form-input--error")
						placeholder = errorMsg
					}
				}
			}
		}

		// POST /admin/patreon/settings - Save Patreon settings
		hx.post("/settings") {
			val params = call.receiveParameters()
			val enabled = params["enabled"] == "true"
			val campaignId = params["campaignId"]?.trim().orEmpty()
			val accessToken = params["accessToken"]?.trim().orEmpty()
			val webhookSecret = params["webhookSecret"]?.trim().orEmpty()
			val patreonUrl = params["patreonUrl"]?.trim().orEmpty()
			val minimumAmountStr = params["minimumAmount"]?.trim().orEmpty()
			val pollIntervalStr = params["pollInterval"]?.trim().orEmpty()

			val currentConfig = configRepository.get(AdminServerConfig.PATREON_CONFIG)

			// Validation: if trying to enable, required fields must be filled
			if (enabled) {
				val effectiveAccessToken = accessToken.ifEmpty { currentConfig.creatorAccessToken }

				if (campaignId.isEmpty()) {
					call.response.header(HxResponseHeaders.Retarget, "#patreon-error")
					call.response.header(HxResponseHeaders.Reswap, "innerHTML")
					val errorMsg = call.msg("admin_patreon_error_campaign_required")
					call.respondHtmlFragment {
						div("error-message") {
							+errorMsg
						}
					}
					return@post
				}

				if (effectiveAccessToken.isEmpty()) {
					call.response.header(HxResponseHeaders.Retarget, "#patreon-error")
					call.response.header(HxResponseHeaders.Reswap, "innerHTML")
					val errorMsg = call.msg("admin_patreon_error_token_required")
					call.respondHtmlFragment {
						div("error-message") {
							+errorMsg
						}
					}
					return@post
				}
			}

			val minimumAmountCents = try {
				(minimumAmountStr.toDoubleOrNull()?.times(100))?.toInt() ?: 500
			} catch (_: Exception) {
				500
			}

			val pollIntervalMinutes = pollIntervalStr.toIntOrNull()?.coerceAtLeast(1) ?: 60

			val newConfig = currentConfig.copy(
				enabled = enabled,
				campaignId = campaignId,
				creatorAccessToken = accessToken.ifEmpty { currentConfig.creatorAccessToken },
				webhookSecret = webhookSecret.ifEmpty { currentConfig.webhookSecret },
				patreonUrl = patreonUrl.ifEmpty { currentConfig.patreonUrl },
				minimumAmountCents = minimumAmountCents,
				pollIntervalMinutes = pollIntervalMinutes
			)

			configRepository.set(AdminServerConfig.PATREON_CONFIG, newConfig)

			call.response.header(HxResponseHeaders.Refresh, "true")
			call.respond(HttpStatusCode.OK, "")
		}

		// POST /admin/patreon/sync - Trigger manual sync
		hx.post("/sync") {
			val config = configRepository.get(AdminServerConfig.PATREON_CONFIG)

			// Don't allow sync if not enabled
			if (!config.enabled) {
				call.response.header(HxResponseHeaders.Retarget, "#patreon-error")
				call.response.header(HxResponseHeaders.Reswap, "innerHTML")
				val errorMsg = call.msg("admin_patreon_error_not_enabled")
				call.respondHtmlFragment {
					div("error-message") {
						+errorMsg
					}
				}
				return@post
			}

			val result = patreonSyncService.performFullSync()
			call.response.header(HxResponseHeaders.Refresh, "true")
			call.respond(HttpStatusCode.OK, "")
		}
	}
}

// GET /admin/patreon - Patreon Integration page
internal fun Route.adminPatreonPage(
	configRepository: ConfigRepository,
	patreonSyncService: PatreonSyncService,
	emailFeatureEnabled: Boolean
) {
	get("/patreon") {
		val patreonConfig = configRepository.get(AdminServerConfig.PATREON_CONFIG)
		val patreonMemberCount = patreonSyncService.getPatreonWhitelistCount()
		val patreonMembersWithAccounts = patreonSyncService.getPatreonMembersWithAccountsCount()

		val model = mapOf(
			"page_stylesheet" to "/assets/css/admin.css",
			"activeSettings" to false,
			"activeWhitelist" to false,
			"activeUsers" to false,
			"activePatreon" to true,
			"activeEmail" to false,
			"patreonFeatureEnabled" to true,
			"emailFeatureEnabled" to emailFeatureEnabled,
			"patreonEnabled" to patreonConfig.enabled,
			"campaignId" to patreonConfig.campaignId,
			"accessToken" to patreonConfig.creatorAccessToken,
			"webhookSecret" to patreonConfig.webhookSecret,
			"patreonUrl" to patreonConfig.patreonUrl,
			"minimumAmountDollars" to "%.2f".format(patreonConfig.minimumAmountCents / 100.0),
			"pollIntervalMinutes" to patreonConfig.pollIntervalMinutes,
			"lastSync" to formatPatreonDate(patreonConfig.lastSync).ifEmpty { "Never" },
			"patreonMemberCount" to patreonMemberCount,
			"patreonMembersWithAccounts" to patreonMembersWithAccounts,
			"webhookUrl" to "/patreon/webhook"
		)
		call.respond(MustacheContent("admin-patreon.mustache", call.withDefaults(model)))
	}
}