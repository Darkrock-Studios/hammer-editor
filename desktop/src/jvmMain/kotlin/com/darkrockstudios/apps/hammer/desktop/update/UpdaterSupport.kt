package com.darkrockstudios.apps.hammer.desktop.update

import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.base.DistributionChannel
import com.darkrockstudios.apps.hammer.base.PackageFormat
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdater
import com.darkrockstudios.apps.hammer.common.data.appupdate.NoOpAppUpdater
import dev.nucleusframework.updater.NucleusUpdater
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

/** Only the direct-download vehicles update themselves; every store updates the app itself. */
fun channelMayUpdate(channel: DistributionChannel): Boolean =
	channel == DistributionChannel.GITHUB || channel == DistributionChannel.APPIMAGE

/**
 * The Nucleus executable type whose release asset this install updates from, or null when the
 * format has no self-update path at all. The EXE is a wrapped MSI, so the MSI upgrades either.
 * The DMG and PKG both leave a bundle that only a signed zip could swap (see
 * docs/IN-APP-UPDATES-MACOS.md), so they are told about releases without downloading them.
 */
fun nucleusExecutableType(format: PackageFormat): String? = when (format) {
	PackageFormat.MSI, PackageFormat.EXE -> "msi"
	PackageFormat.DEB -> "deb"
	PackageFormat.RPM -> "rpm"
	PackageFormat.APPIMAGE -> "appimage"
	PackageFormat.DMG, PackageFormat.PKG -> "dmg"
	PackageFormat.NONE, PackageFormat.MSIX, PackageFormat.SNAP, PackageFormat.FLATPAK -> null
}

/** Formats Nucleus installs and relaunches on its own; the rest are told and sent to the release page. */
fun canInstallInPlace(format: PackageFormat): Boolean = when (format) {
	PackageFormat.MSI, PackageFormat.EXE, PackageFormat.DEB, PackageFormat.RPM, PackageFormat.APPIMAGE -> true
	PackageFormat.NONE, PackageFormat.DMG, PackageFormat.PKG, PackageFormat.MSIX,
	PackageFormat.SNAP, PackageFormat.FLATPAK -> false
}

/**
 * Thar be dragons: this is the only place a [NucleusUpdater] may be constructed. Its constructor
 * opens a `java.net.http.HttpClient`, whose NIO selector crashes under the MSIX sandbox (the
 * same bug that moved Ktor to OkHttp in #938), so it must never be built on a store channel,
 * not even to read a version.
 */
fun createAppUpdater(
	channel: DistributionChannel,
	format: PackageFormat,
	devMode: Boolean,
	preferences: UpdatePreferences,
	appScope: CoroutineScope,
	ioDispatcher: CoroutineContext,
	currentVersion: String = BuildMetadata.APP_VERSION,
): AppUpdater {
	if (!channelMayUpdate(channel) || devMode) return NoOpAppUpdater
	val executableType = nucleusExecutableType(format) ?: return NoOpAppUpdater

	val updater = NucleusUpdater {
		this.currentVersion = currentVersion
		provider = HammerReleaseProvider()
		this.executableType = executableType
		differentialDownload = false
	}
	return NucleusAppUpdater(
		updater = updater,
		installable = canInstallInPlace(format),
		preferences = preferences,
		scope = appScope,
		ioDispatcher = ioDispatcher,
	).also { it.start() }
}
