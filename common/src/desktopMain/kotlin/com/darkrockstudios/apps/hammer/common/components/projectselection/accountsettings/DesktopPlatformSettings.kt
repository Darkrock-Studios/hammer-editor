package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.serialization.Serializable

interface DesktopPlatformSettings : PlatformSettings {
	val state: Value<PlatformState>

	fun setProjectsDir(path: String)

	@Serializable
	data class PlatformState(
		val projectsDir: HPath,
	)
}
