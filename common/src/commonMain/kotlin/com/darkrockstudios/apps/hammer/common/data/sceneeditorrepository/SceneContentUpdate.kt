package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.UpdateSource

data class SceneContentUpdate(
	val content: SceneContent,
	val source: UpdateSource,
	/** Orders updates against [SceneContentRepository.replaceBuffer], so one queued before a replace is dropped. */
	val sequence: Long,
)