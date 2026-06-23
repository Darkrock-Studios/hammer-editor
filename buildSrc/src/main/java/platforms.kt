package com.darkrockstudios.build

/**
 * Targets Hammer publishes to. A "full" release publishes to every client app
 * store; a "single-store" release publishes to a subset; a "server only"
 * release publishes to none of them and instead tags the hammer.ink backend.
 *
 * The subset is encoded as a `+token+token` suffix on the git release tag
 * (semver build-metadata syntax). Example:
 *   - All stores:           v1.2.3
 *   - Google Play only:     v1.2.3+google-play
 *   - Play + F-Droid:       v1.2.3+fdroid+google-play
 *   - Server only:          v1.2.3+server
 *
 * `publish-release.yml` reads the suffix to decide which per-store reusable
 * workflows to call. [SERVER] has no per-store job, so a `+server` tag matches
 * none of them — the server distribution is built on every release and deployed
 * out of band. The order in `tagSuffix` is enum-declaration order so tag names
 * are deterministic regardless of UI selection order.
 */
enum class Platform(val tagToken: String, val displayName: String) {
	GOOGLE_PLAY("google-play", "Google Play"),
	FDROID("fdroid", "F-Droid"),
	SNAP("snap", "Snap"),
	MS_STORE("ms-store", "MS Store"),
	MAC_APP_STORE("mac-app-store", "Mac App Store"),
	IOS_APP_STORE("ios-app-store", "iOS App Store"),
	SERVER("server", "Server"),
	;

	companion object {
		val ALL: Set<Platform> = values().toSet()

		/** Client app stores — the per-store options offered in "Targeted" mode. */
		val CLIENT_STORES: Set<Platform> = ALL - SERVER
	}
}

/**
 * Returns the tag suffix for a set of selected platforms.
 *
 * - Full release (all platforms): empty string — produces `vX.Y.Z`.
 * - Subset: `+token1+token2...` in enum-declaration order.
 * - Empty set: throws — a release with no stores is meaningless.
 */
fun tagSuffix(platforms: Set<Platform>): String {
	require(platforms.isNotEmpty()) { "Release must target at least one platform" }
	if (platforms == Platform.ALL) return ""
	return Platform.values()
		.filter { it in platforms }
		.joinToString(separator = "") { "+${it.tagToken}" }
}

/**
 * Returns true if `tag` is a release tag produced by `prepareForRelease` for
 * the given `bareVersionTag` (e.g. "v1.2.4") — i.e. the bare tag itself, or
 * `bareVersionTag+token+token` where every token is a known [Platform.tagToken].
 *
 * Used by `backoutLastRelease` / `revertLastRelease` to filter the
 * `vX.Y.Z+*` glob so unrelated tags (`vX.Y.Z+rc1`, `vX.Y.Z+sbom`, etc.) that
 * share the version prefix aren't force-deleted alongside our own.
 */
fun isPlatformReleaseTag(tag: String, bareVersionTag: String): Boolean {
	if (tag == bareVersionTag) return true
	val prefix = "$bareVersionTag+"
	if (!tag.startsWith(prefix)) return false
	val suffix = tag.removePrefix(prefix)
	if (suffix.isEmpty()) return false
	val known = Platform.values().mapTo(mutableSetOf()) { it.tagToken }
	return suffix.split('+').all { it in known }
}
