package com.darkrockstudios.apps.hammer.plugins

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpCachingTest {

	private fun maxAge(contentType: ContentType?): Int? =
		(staticAssetCaching(contentType)?.cacheControl as? CacheControl.MaxAge)?.maxAgeSeconds

	@Test
	fun `css and js are cached for a day`() {
		assertEquals(86_400, maxAge(ContentType.Text.CSS))
		assertEquals(86_400, maxAge(ContentType.Application.JavaScript))
		assertEquals(86_400, maxAge(ContentType("text", "javascript")))
	}

	@Test
	fun `images and fonts are cached for a week`() {
		assertEquals(604_800, maxAge(ContentType.Image.PNG))
		assertEquals(604_800, maxAge(ContentType("font", "woff2")))
	}

	@Test
	fun `static asset caching is public`() {
		val cacheControl = staticAssetCaching(ContentType.Text.CSS)?.cacheControl as? CacheControl.MaxAge
		assertEquals(CacheControl.Visibility.Public, cacheControl?.visibility)
	}

	@Test
	fun `html xml and unknown content types are not cached`() {
		assertNull(staticAssetCaching(ContentType.Text.Html))
		assertNull(staticAssetCaching(ContentType.Application.Xml))
		assertNull(staticAssetCaching(null))
	}

	@Test
	fun `content-type parameters are ignored`() {
		assertEquals(86_400, maxAge(ContentType.Text.CSS.withParameter("charset", "utf-8")))
	}
}
