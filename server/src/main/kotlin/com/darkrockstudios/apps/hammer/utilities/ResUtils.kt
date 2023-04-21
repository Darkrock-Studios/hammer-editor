package com.darkrockstudios.apps.hammer.utilities

import java.io.File
import java.io.InputStream
import java.net.URISyntaxException
import java.net.URL

object ResUtils {
	fun getResourceAsBytes(fileName: String): ByteArray {
		return getResourceAsStream(fileName).readAllBytes()
	}

	fun getResourceAsStream(fileName: String): InputStream {
		val classLoader = ResUtils::class.java.classLoader
		val inputStream = classLoader.getResourceAsStream(fileName)

		// the stream holding the file content
		return inputStream ?: throw IllegalArgumentException("file not found! $fileName")
	}

	@Throws(URISyntaxException::class)
	fun getFileFromResource(fileName: String): File {
		val classLoader = ResUtils::class.java.classLoader
		val resource: URL? = classLoader.getResource(fileName)
		return if (resource == null) {
			throw java.lang.IllegalArgumentException("file not found! $fileName")
		} else {
			// failed if files have whitespaces or special characters
			//return new File(resource.getFile());
			File(resource.toURI())
		}
	}
}