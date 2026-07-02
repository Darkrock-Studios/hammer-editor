package com.darkrockstudios.apps.hammer.common

import platform.UIKit.UIDevice

actual fun platformStartupInfo(): String {
	val device = UIDevice.currentDevice
	return "OS: ${device.systemName} ${device.systemVersion} | device: ${device.model}"
}
