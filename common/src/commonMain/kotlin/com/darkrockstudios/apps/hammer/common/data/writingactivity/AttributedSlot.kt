package com.darkrockstudios.apps.hammer.common.data.writingactivity

import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingActivitySlot
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectOwner

/**
 * A slot with its author resolved: a slot with no author is the owner's. [authorId] is null
 * only when the owner is unknown (an own project with no account signed in).
 */
data class AttributedSlot(
	val deviceId: String,
	val log: DeviceLog,
	val authorId: Long?,
	val authorName: String?,
)

/** The owner of a project: [sharedOwner] for a shared one, else the signed-in account. */
fun activityOwner(sharedOwner: ProjectOwner?, serverSettings: ServerSettings?): ProjectOwner? =
	sharedOwner ?: serverSettings?.userId?.takeIf { it >= 0 }?.let { ProjectOwner(it, null) }

fun attributeSlots(slots: List<WritingActivitySlot>, owner: ProjectOwner?): List<AttributedSlot> =
	slots.map { slot ->
		val authorId = slot.log.authorId ?: owner?.id
		val authorName = slot.log.authorName ?: owner?.name?.takeIf { authorId == owner.id }
		AttributedSlot(slot.deviceId, slot.log, authorId, authorName)
	}

/**
 * The slots whose words are [viewerId]'s: those attributed to them, plus this device's own slot
 * before the server has stamped an author on it. With no account ([viewerId] null), every slot.
 */
fun List<AttributedSlot>.viewersSlots(viewerId: Long?, ownDeviceId: String): List<AttributedSlot> =
	if (viewerId == null) {
		this
	} else {
		filter { it.authorId == viewerId || (it.deviceId == ownDeviceId && it.log.authorId == null) }
	}
