package com.darkrockstudios.apps.hammer.patreon

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import org.slf4j.Logger

class PatreonWebhookHandler(
	private val patreonApiClient: PatreonApiClient,
	private val patreonSyncService: PatreonSyncService,
	private val configRepository: ConfigRepository,
	private val logger: Logger
) {
	suspend fun handleWebhook(
		payload: String,
		signature: String?,
		eventType: String?
	): WebhookResult {
		val config = configRepository.get(AdminServerConfig.PATREON_CONFIG)

		if (!config.enabled) {
			logger.info("Patreon webhook received but integration is disabled")
			return WebhookResult.Disabled
		}

		if (signature.isNullOrBlank()) {
			logger.warn("Patreon webhook missing signature header")
			return WebhookResult.MissingSignature
		}

		if (config.webhookSecret.isBlank()) {
			logger.warn("Patreon webhook secret not configured")
			return WebhookResult.NotConfigured
		}

		// Verify signature
		if (!patreonApiClient.verifyWebhookSignature(payload, signature, config.webhookSecret)) {
			logger.warn("Patreon webhook signature verification failed")
			return WebhookResult.InvalidSignature
		}

		logger.info("Received valid Patreon webhook: $eventType")

		// On any member event, trigger a full sync
		// This is the simplest and most reliable approach
		val syncResult = patreonSyncService.performFullSync()

		return if (syncResult.isSuccess) {
			WebhookResult.Success(syncResult.getOrThrow())
		} else {
			WebhookResult.SyncFailed(syncResult.exceptionOrNull()?.message ?: "Unknown error")
		}
	}
}

sealed class WebhookResult {
	data object Disabled : WebhookResult()
	data object MissingSignature : WebhookResult()
	data object NotConfigured : WebhookResult()
	data object InvalidSignature : WebhookResult()
	data class Success(val syncResult: SyncResult) : WebhookResult()
	data class SyncFailed(val error: String) : WebhookResult()
}
