package com.darkrockstudios.apps.hammer.common

import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.base.DistributionChannel
import io.github.aakira.napier.Napier

/**
 * Logs a single identifying line as early as possible in startup: the build version plus
 * platform/environment details. First line in every log, so user-submitted logs are
 * self-describing (version, channel, OS, display server, …) without a round-trip to ask.
 */
fun logStartupBanner() {
	Napier.i(startupBanner())
}

/** The identifying line itself, shared by the startup banner and the crash dumps. */
fun startupBanner(): String =
	"Hammer v${BuildMetadata.APP_VERSION}" +
		" | channel: ${DistributionChannel.current.token}" +
		" | ${platformStartupInfo()}"

/** Platform + environment details for the startup banner. */
expect fun platformStartupInfo(): String
