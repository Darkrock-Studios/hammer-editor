package com.darkrockstudios.apps.hammer.common.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The author's contact block on a manuscript's first page. Author PII rather than project data, so it
 * lives in the local global settings and is never synced. Blank fields are left out of the block.
 *
 * @param address may span several lines.
 */
@Immutable
@Serializable
data class ManuscriptContact(
	val legalName: String = "",
	val address: String = "",
	val phone: String = "",
	val email: String = "",
)
