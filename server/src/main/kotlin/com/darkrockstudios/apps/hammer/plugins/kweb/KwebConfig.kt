package com.darkrockstudios.apps.hammer.plugins.kweb

import kweb.config.KwebConfiguration
import java.time.Duration

val hammerKwebConfig = object : KwebConfiguration() {
	override val buildpageTimeout = Duration.ofSeconds(5)
	override val clientStateStatsEnabled = false
	override val clientStateTimeout = Duration.ofHours(1)
}