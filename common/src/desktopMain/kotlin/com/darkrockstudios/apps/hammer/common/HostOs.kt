package com.darkrockstudios.apps.hammer.common

enum class HostOs { Windows, Linux, MacOs, Other }

val hostOs: HostOs by lazy {
	val name = System.getProperty("os.name", "").lowercase()
	when {
		"win" in name -> HostOs.Windows
		"mac" in name || "darwin" in name -> HostOs.MacOs
		"linux" in name || "nix" in name || "nux" in name || "bsd" in name -> HostOs.Linux
		else -> HostOs.Other
	}
}
