package com.darkrockstudios.apps.hammer.common.components.actions

import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.hostOs

actual val currentChordPlatform: ChordPlatform
	get() = if (hostOs == HostOs.MacOs) ChordPlatform.DesktopMac else ChordPlatform.Desktop
