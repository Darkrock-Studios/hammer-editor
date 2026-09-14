package com.darkrockstudios.apps.hammer.base

/**
 * The distribution vehicle this build was produced for, baked in at build time from
 * `-Pchannel=<token>`. Branch on [current] for the rules that vary per vehicle: self-update
 * (forbidden in the app stores, expected for the GitHub and AppImage builds), where "get the app"
 * links point, external-payment policy, and sandbox-aware path resolution.
 *
 * Deliberately a flat enum rather than a set of capability flags. Every branch is a greppable
 * `when` on this type, so each channel-conditional behaviour is findable in one search; flags can
 * be added later if two channels start sharing a trait and branches begin to duplicate.
 *
 * Because every branch compiles into every build, a store-forbidden code path is physically
 * present in a store binary even when unreachable. If a store ever objects to presence rather
 * than behaviour, that is the trigger to promote that one case to a real source set.
 *
 * Kept in step with the build-side enum in `buildSrc/src/main/java/distributionChannel.kt`.
 */
enum class DistributionChannel(val token: String, val displayName: String) {
	DEV("dev", "Development"),
	GITHUB("github", "GitHub"),
	PLAY("google-play", "Google Play"),
	FDROID("fdroid", "F-Droid"),
	SNAP("snap", "Snap"),
	APPIMAGE("appimage", "AppImage"),
	FLATHUB("flathub", "Flathub"),
	MICROSOFT_STORE("ms-store", "Microsoft Store"),
	MAC_APP_STORE("mac-app-store", "Mac App Store"),
	IOS_APP_STORE("ios-app-store", "iOS App Store"),
	;

	companion object {
		/**
		 * An unrecognised token falls back to [DEV] rather than throwing: the build validates the
		 * token, so reaching here with an unknown one means something is already wrong and taking
		 * down the app over a log-header string would be worse.
		 */
		val current: DistributionChannel =
			entries.firstOrNull { it.token == BuildMetadata.CHANNEL } ?: DEV
	}
}
