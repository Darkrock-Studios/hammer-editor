package com.darkrockstudios.apps.hammer.desktop.update

import com.darkrockstudios.apps.hammer.base.DistributionChannel
import com.darkrockstudios.apps.hammer.base.PackageFormat
import com.darkrockstudios.apps.hammer.common.data.appupdate.NoOpAppUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdaterSupportTest {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	@AfterEach
	fun tearDown() {
		scope.cancel()
	}

	@Test
	fun `only the direct download channels may update`() {
		val allowed = DistributionChannel.entries.filter(::channelMayUpdate)
		assertEquals(listOf(DistributionChannel.GITHUB, DistributionChannel.APPIMAGE), allowed)
	}

	@Test
	fun `every format maps to the asset it updates from`() {
		assertEquals("msi", nucleusExecutableType(PackageFormat.MSI))
		assertEquals("msi", nucleusExecutableType(PackageFormat.EXE))
		assertEquals("deb", nucleusExecutableType(PackageFormat.DEB))
		assertEquals("rpm", nucleusExecutableType(PackageFormat.RPM))
		assertEquals("appimage", nucleusExecutableType(PackageFormat.APPIMAGE))
		assertEquals("dmg", nucleusExecutableType(PackageFormat.DMG))
		assertEquals("dmg", nucleusExecutableType(PackageFormat.PKG))
		for (format in listOf(PackageFormat.NONE, PackageFormat.MSIX, PackageFormat.SNAP, PackageFormat.FLATPAK)) {
			assertNull(nucleusExecutableType(format), format.token)
		}
	}

	@Test
	fun `macOS installs are announced but not installed in place`() {
		assertTrue(canInstallInPlace(PackageFormat.MSI))
		assertTrue(canInstallInPlace(PackageFormat.EXE))
		assertTrue(canInstallInPlace(PackageFormat.DEB))
		assertTrue(canInstallInPlace(PackageFormat.RPM))
		assertTrue(canInstallInPlace(PackageFormat.APPIMAGE))
		assertFalse(canInstallInPlace(PackageFormat.DMG))
		assertFalse(canInstallInPlace(PackageFormat.PKG))
	}

	@Test
	fun `store channels, dev runs, and unpackaged builds get the no-op updater`() {
		assertSame(NoOpAppUpdater, create(DistributionChannel.MICROSOFT_STORE, PackageFormat.MSIX))
		assertSame(NoOpAppUpdater, create(DistributionChannel.MAC_APP_STORE, PackageFormat.PKG))
		assertSame(NoOpAppUpdater, create(DistributionChannel.SNAP, PackageFormat.SNAP))
		assertSame(NoOpAppUpdater, create(DistributionChannel.DEV, PackageFormat.NONE))
		assertSame(NoOpAppUpdater, create(DistributionChannel.GITHUB, PackageFormat.NONE))
		assertSame(NoOpAppUpdater, create(DistributionChannel.GITHUB, PackageFormat.MSI, devMode = true))
	}

	@Test
	fun `the GitHub and AppImage builds get a real updater`() {
		assertTrue(create(DistributionChannel.GITHUB, PackageFormat.MSI) is NucleusAppUpdater)
		assertTrue(create(DistributionChannel.GITHUB, PackageFormat.DMG) is NucleusAppUpdater)
		assertTrue(create(DistributionChannel.APPIMAGE, PackageFormat.APPIMAGE) is NucleusAppUpdater)
	}

	private fun create(channel: DistributionChannel, format: PackageFormat, devMode: Boolean = false) =
		createAppUpdater(
			channel = channel,
			format = format,
			devMode = devMode,
			preferences = FakeUpdatePreferences(),
			appScope = scope,
			ioDispatcher = Dispatchers.IO,
			currentVersion = "1.0.0",
		)
}
