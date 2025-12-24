package com.darkrockstudios.apps.hammer.frontend.utils

import java.net.URLDecoder
import java.net.URLEncoder

object ProjectName {
	/**
	 * Decode a URL segment: URL decode then replace dashes with spaces.
	 * Example: "My-Story-Name" -> "My Story Name"
	 */
	fun decodeFromUrl(urlSegment: String): String =
		URLDecoder.decode(urlSegment, "UTF-8").replace('-', ' ')

	/**
	 * Format a string for use in a URL: replace spaces with dashes, then URL encode.
	 * Example: "My Story Name" -> "My-Story-Name"
	 */
	fun formatForUrl(name: String): String =
		URLEncoder.encode(name.replace(' ', '-'), "UTF-8")
}