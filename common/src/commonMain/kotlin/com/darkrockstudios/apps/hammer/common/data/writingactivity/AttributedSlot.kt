package com.darkrockstudios.apps.hammer.common.data.writingactivity

import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingActivitySlot
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectOwner

/**
 * A slot with its author resolved: a slot with no author is the owner's, except this device's
 * own slot, which is the signed-in account's. [authorId] is null only when neither is known (an
 * own project with no account signed in).
 */
data class AttributedSlot(
	val deviceId: String,
	val log: DeviceLog,
	val authorId: Long?,
	val authorName: String?,
)

/** The signed-in account whose writing activity this device records, or null with none. */
fun activityViewerId(serverSettings: ServerSettings?): Long? = serverSettings?.userId?.takeIf { it >= 0 }

/** The owner of a project: [sharedOwner] for a shared one, else the signed-in account. */
fun activityOwner(sharedOwner: ProjectOwner?, serverSettings: ServerSettings?): ProjectOwner? =
	sharedOwner ?: activityViewerId(serverSettings)?.let { ProjectOwner(it, null) }

/**
 * Resolves each slot's author. A slot with no author is [owner]'s, unless it is [ownDeviceId]'s
 * slot and [viewerId] is signed in: this device writes only for the signed-in account, before the
 * server stamps the slot on sync.
 */
fun attributeSlots(
	slots: List<WritingActivitySlot>,
	owner: ProjectOwner?,
	viewerId: Long? = null,
	ownDeviceId: String? = null,
): List<AttributedSlot> =
	slots.map { slot ->
		val unstampedOwn = viewerId != null && slot.log.authorId == null && slot.deviceId == ownDeviceId
		val authorId = slot.log.authorId ?: if (unstampedOwn) viewerId else owner?.id
		val authorName = slot.log.authorName ?: owner?.name?.takeIf { authorId == owner.id }
		AttributedSlot(slot.deviceId, slot.log, authorId, authorName)
	}

/** The slots attributed to [viewerId]; with no account signed in, every slot. */
fun List<AttributedSlot>.viewersSlots(viewerId: Long?): List<AttributedSlot> =
	if (viewerId == null) this else filter { it.authorId == viewerId }
