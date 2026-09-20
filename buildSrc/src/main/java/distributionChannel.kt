package com.darkrockstudios.build

/**
 * Build-side mirror of the runtime `DistributionChannel` enum in `:base`. The two lists have to
 * stay in step; `DistributionChannelTest` in `:base` pins the token strings so a change to one
 * without the other fails a test rather than shipping a build that reports the wrong channel.
 *
 * Tokens match [Platform.tagToken] wherever the two overlap.
 */
enum class DistributionChannel(val token: String) {
	DEV("dev"),
	GITHUB("github"),
	PLAY("google-play"),
	FDROID("fdroid"),
	SNAP("snap"),
	APPIMAGE("appimage"),
	FLATHUB("flathub"),
	MICROSOFT_STORE("ms-store"),
	MAC_APP_STORE("mac-app-store"),
	IOS_APP_STORE("ios-app-store"),
	;

	companion object {
		fun fromToken(token: String): DistributionChannel? = entries.firstOrNull { it.token == token }
	}
}

/**
 * Resolves the channel for this build from `-Pchannel=<token>`, defaulting to [FDROID] when the
 * F-Droid build flags are set and [DEV] otherwise. Throws on an unknown token, so a typo in a
 * release workflow fails the build instead of quietly shipping a store binary as [DEV].
 */
fun resolveDistributionChannel(
	channelProperty: String?,
	isFDroid: Boolean,
): DistributionChannel {
	val requested = channelProperty?.trim()?.takeIf { it.isNotEmpty() }
		?: return if (isFDroid) DistributionChannel.FDROID else DistributionChannel.DEV

	return DistributionChannel.fromToken(requested)
		?: error(
			"Unknown -Pchannel=$requested. Valid channels: " +
				DistributionChannel.entries.joinToString(", ") { it.token }
		)
}
