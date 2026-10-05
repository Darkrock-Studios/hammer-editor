package com.darkrockstudios.apps.hammer.desktop.update

import dev.nucleusframework.core.runtime.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HammerReleaseProviderTest {

	@AfterEach
	fun clearOverride() {
		System.clearProperty(HammerReleaseProvider.BASE_URL_PROPERTY)
	}

	@Test
	fun `metadata lives on the latest release, one file per platform`() {
		val provider = HammerReleaseProvider()
		val base = "https://github.com/Darkrock-Studios/hammer-editor/releases/latest/download"

		assertEquals("$base/latest.yml", provider.getUpdateMetadataUrl("latest", Platform.Windows))
		assertEquals("$base/latest-mac.yml", provider.getUpdateMetadataUrl("latest", Platform.MacOS))
		assertEquals("$base/latest-linux.yml", provider.getUpdateMetadataUrl("latest", Platform.Linux))
		assertEquals("$base/latest.yml", provider.getUpdateMetadataUrl("latest", Platform.Unknown))
	}

	@Test
	fun `downloads ignore the version and use the fixed asset name`() {
		val provider = HammerReleaseProvider()

		assertEquals(
			"https://github.com/Darkrock-Studios/hammer-editor/releases/latest/download/hammer.msi",
			provider.getDownloadUrl("hammer.msi", "3.12.0"),
		)
	}

	@Test
	fun `a system property substitutes a local server`() {
		System.setProperty(HammerReleaseProvider.BASE_URL_PROPERTY, "http://localhost:8000/")
		val provider = HammerReleaseProvider()

		assertEquals("http://localhost:8000/latest.yml", provider.getUpdateMetadataUrl("latest", Platform.Windows))
		assertEquals("http://localhost:8000/hammer.deb", provider.getDownloadUrl("hammer.deb", "1.0.0"))
	}

	@Test
	fun `a blank override falls back to GitHub`() {
		System.setProperty(HammerReleaseProvider.BASE_URL_PROPERTY, "  ")

		assertEquals(
			"https://github.com/Darkrock-Studios/hammer-editor/releases/latest/download",
			HammerReleaseProvider.defaultBaseUrl(),
		)
	}
}
