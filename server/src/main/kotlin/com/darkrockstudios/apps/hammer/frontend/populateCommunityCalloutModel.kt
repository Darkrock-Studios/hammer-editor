package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository

internal suspend fun populateCommunityCalloutModel(
	serverConfig: ServerConfig,
	model: MutableMap<String, Any>,
	accountsRepository: AccountsRepository,
	projectAccessRepository: ProjectAccessRepository
) {
	if (serverConfig.communityEnabled) {
		model["communityEnabled"] = true
		val authorCount = accountsRepository.countCommunityAuthors()
		val storyCount = projectAccessRepository.countCommunityFeedStories()

		model["communityAuthorCount"] = authorCount
		model["communityStoryCount"] = storyCount
		model["hasCommunityContent"] = authorCount > 0 && storyCount > 0
		model["hasCommunityAuthorsOnly"] = authorCount > 0 && storyCount == 0L
		model["hasNoCommunityContent"] = authorCount == 0L
	}
}