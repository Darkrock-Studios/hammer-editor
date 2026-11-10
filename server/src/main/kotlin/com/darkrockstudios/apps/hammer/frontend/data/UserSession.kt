package com.darkrockstudios.apps.hammer.frontend.data

import com.darkrockstudios.apps.hammer.project.OwnerId
import com.darkrockstudios.apps.hammer.project.UncheckedOwner
import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
	val userId: Long,
	val username: String,
	val isAdmin: Boolean,
) {
	/** The owner of a project found by this session's own user id; never for a shared project. */
	@UncheckedOwner
	val asOwner: OwnerId
		get() = OwnerId.unchecked(userId)
}
