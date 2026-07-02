package com.darkrockstudios.apps.hammer.common

import com.darkrockstudios.apps.hammer.base.BuildMetadata
import io.github.aakira.napier.Napier

/**
 * Logs a single identifying line as early as possible in startup: the build version plus
 * platform/environment details. First line in every log, so user-submitted logs are
 * self-describing (version, OS, display server, …) without a round-trip to ask.
 */
fun logStartupBanner() {
	Napier.i("Hammer v${BuildMetadata.APP_VERSION} | ${platformStartupInfo()}")
}

/** Platform + environment details for the startup banner. */
expect fun platformStartupInfo(): String
