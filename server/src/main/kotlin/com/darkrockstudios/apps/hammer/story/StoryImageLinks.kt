package com.darkrockstudios.apps.hammer.story

/**
 * Story HTML keeps an embedded image as `<img src="hammer://image/<id>">`, so a rendered page, and
 * the disk cache holding it, does not depend on who asked for it. [resolve] swaps those sources
 * for real URLs as the page is served.
 */
object StoryImageLinks {
	/**
	 * Only a `src` attribute is rewritten. The sanitizer escapes the quotes in text, so prose that
	 * merely mentions a reference can never look like one here.
	 */
	private val IMAGE_SRC = Regex("""(?<=\s)src="hammer://image/([0-9]{1,9})"""")

	/** Replaces every image reference in [html] with [urlFor] of its image id. */
	suspend fun resolve(html: String, urlFor: suspend (imageId: Int) -> String): String {
		val ids = IMAGE_SRC.findAll(html).map { it.groupValues[1].toInt() }.toSet()
		if (ids.isEmpty()) return html

		val urls = ids.associateWith { escapeAttribute(urlFor(it)) }
		return IMAGE_SRC.replace(html) { match ->
			"src=\"${urls.getValue(match.groupValues[1].toInt())}\""
		}
	}

	private fun escapeAttribute(value: String): String = value
		.replace("&", "&amp;")
		.replace("\"", "&quot;")
		.replace("<", "&lt;")
		.replace(">", "&gt;")
}
