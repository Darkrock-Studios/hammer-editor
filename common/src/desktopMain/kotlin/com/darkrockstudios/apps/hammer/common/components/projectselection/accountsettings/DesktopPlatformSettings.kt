package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.serialization.Serializable

interface DesktopPlatformSettings : PlatformSettings {
	val state: Value<PlatformState>

	fun setProjectsDir(path: String)
	fun setAutomaticUpdateChecks(value: Boolean)

	@Serializable
	data class PlatformState(
		val projectsDir: HPath,
		/** False on builds that cannot update themselves, which then show no update setting. */
		val updateChecksSupported: Boolean = false,
		val automaticUpdateChecks: Boolean = true,
	)
}
