package com.darkrockstudios.apps.hammer.plugins

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpCachingTest {

	private fun maxAge(path: String, contentType: ContentType?): Int? =
		(staticAssetCaching(path, contentType)?.cacheControl as? CacheControl.MaxAge)?.maxAgeSeconds

	private fun header(path: String, contentType: ContentType?, versioned: Boolean): String? =
		staticAssetCaching(path, contentType, versioned)?.cacheControl?.toString()

	@Test
	fun `asset css and js are cached for a day`() {
		assertEquals(86_400, maxAge("/assets/css/base.css", ContentType.Text.CSS))
		assertEquals(86_400, maxAge("/assets/js/home.js", ContentType.Application.JavaScript))
		assertEquals(86_400, maxAge("/assets/js/home.js", ContentType("text", "javascript")))
	}

	@Test
	fun `asset images and fonts are cached for a week`() {
		assertEquals(604_800, maxAge("/assets/images/og-default.png", ContentType.Image.PNG))
		assertEquals(604_800, maxAge("/assets/Kingthings.woff2", ContentType("font", "woff2")))
	}

	@Test
	fun `version stamped assets are cached for a year and immutable`() {
		val expected = "max-age=31536000, public, immutable"
		assertEquals(expected, header("/assets/css/base.css", ContentType.Text.CSS, versioned = true))
		assertEquals(expected, header("/assets/js/home.js", ContentType.Application.JavaScript, versioned = true))
		assertEquals(expected, header("/assets/images/og-default.png", ContentType.Image.PNG, versioned = true))
		assertEquals(expected, header("/assets/Kingthings.woff2", ContentType("font", "woff2"), versioned = true))
	}

	@Test
	fun `assets referenced without a version keep the shorter windows`() {
		assertEquals("max-age=86400, public", header("/assets/css/base.css", ContentType.Text.CSS, versioned = false))
		assertEquals(
			"max-age=604800, public",
			header("/assets/images/masthead-1920.webp", ContentType("image", "webp"), versioned = false)
		)
	}

	@Test
	fun `a version stamp does not make uncacheable content cacheable`() {
		assertNull(staticAssetCaching("/", ContentType.Text.Html, versioned = true))
		assertNull(staticAssetCaching("/assets/data", null, versioned = true))
	}

	@Test
	fun `dynamic OG images are cached for 30 days`() {
		assertEquals(2_592_000, maxAge("/og/a/42.png", ContentType.Image.PNG))
		assertEquals(2_592_000, maxAge("/og/s/some-uuid.png", ContentType.Image.PNG))
	}

	@Test
	fun `caching is public`() {
		val asset = staticAssetCaching("/assets/css/base.css", ContentType.Text.CSS)?.cacheControl as? CacheControl.MaxAge
		assertEquals(CacheControl.Visibility.Public, asset?.visibility)
		val og = staticAssetCaching("/og/a/42.png", ContentType.Image.PNG)?.cacheControl as? CacheControl.MaxAge
		assertEquals(CacheControl.Visibility.Public, og?.visibility)
	}

	@Test
	fun `an image outside asset and og paths is not cached`() {
		assertNull(staticAssetCaching("/a/JaneDoe/story", ContentType.Image.PNG))
	}

	@Test
	fun `html xml and unknown content types are not cached`() {
		assertNull(staticAssetCaching("/", ContentType.Text.Html))
		assertNull(staticAssetCaching("/sitemap.xml", ContentType.Application.Xml))
		assertNull(staticAssetCaching("/assets/data", null))
	}

	@Test
	fun `content-type parameters are ignored`() {
		assertEquals(86_400, maxAge("/assets/css/base.css", ContentType.Text.CSS.withParameter("charset", "utf-8")))
	}
}
