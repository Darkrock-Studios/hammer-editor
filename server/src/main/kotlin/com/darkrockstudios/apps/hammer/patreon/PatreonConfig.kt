package com.darkrockstudios.apps.hammer.patreon

import kotlinx.serialization.Serializable

@Serializable
data class PatreonConfig(
	val enabled: Boolean = false,
	val campaignId: String = "",
	val creatorAccessToken: String = "",
	val webhookSecret: String = "",
	val patreonUrl: String = "",
	val minimumAmountCents: Int = 500,
	val pollIntervalMinutes: Int = 60,
	val lastSync: String = ""
)
