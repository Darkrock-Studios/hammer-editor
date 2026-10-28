package com.darkrockstudios.apps.hammer.story

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StoryImageLinksTest {

	private suspend fun resolve(html: String) = StoryImageLinks.resolve(html) { "/image/$it?s=sig&p=a\"b" }

	@Test
	fun `rewrites an image source to its url`() = runTest {
		val html = """<p><img src="hammer://image/5" alt="Map" /></p>"""

		assertEquals(
			"""<p><img src="/image/5?s=sig&amp;p=a&quot;b" alt="Map" /></p>""",
			resolve(html),
		)
	}

	@Test
	fun `rewrites every image by its own id`() = runTest {
		val html = """<img src="hammer://image/5" /><img src="hammer://image/12" /><img src="hammer://image/5" />"""
		val resolved = StoryImageLinks.resolve(html) { "/i/$it" }

		assertEquals("""<img src="/i/5" /><img src="/i/12" /><img src="/i/5" />""", resolved)
	}

	@Test
	fun `leaves a reference outside a src attribute alone`() = runTest {
		val html = """<p>See hammer://image/5 and src=&#34;hammer://image/5&#34;</p>""" +
			"""<a href="hammer://image/5">x</a><img data-src="hammer://image/5" />"""

		assertEquals(html, resolve(html))
	}

	@Test
	fun `asks for no url when there are no images`() = runTest {
		val html = "<p>Plain prose.</p>"
		val resolved = StoryImageLinks.resolve(html) { error("no image to resolve") }

		assertEquals(html, resolved)
	}
}
