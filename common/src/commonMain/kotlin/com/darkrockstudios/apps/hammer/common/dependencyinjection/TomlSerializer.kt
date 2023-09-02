package com.darkrockstudios.apps.hammer.common.dependencyinjection

import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlIndentation


fun createTomlSerializer(): Toml {
	return Toml {
		ignoreUnknownKeys = true
		indentation = TomlIndentation.Tab
	}
}