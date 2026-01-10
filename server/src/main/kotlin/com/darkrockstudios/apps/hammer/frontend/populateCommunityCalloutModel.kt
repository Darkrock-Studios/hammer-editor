package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository

internal suspend fun populateCommunityCalloutModel(
	serverConfig: ServerConfig,
	model: MutableMap<String, Any>,
	accountsRepository: AccountsRepository
) {
	if (serverConfig.communityEnabled) {
		model["communityEnabled"] = true
		val authorCount = accountsRepository.countCommunityAuthors()
		model["communityAuthorCount"] = authorCount
		model["hasCommunityAuthors"] = authorCount > 0
		model["hasSingleAuthor"] = authorCount == 1L
		model["hasMultipleAuthors"] = authorCount > 1
	}
}