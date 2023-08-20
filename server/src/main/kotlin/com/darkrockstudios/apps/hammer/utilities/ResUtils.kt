package com.darkrockstudios.apps.hammer.utilities

import io.herrera.kevin.resource.Resource
import java.io.InputStream
import java.util.*

object ResUtils {
	private val resource: Resource = Resource(ResUtils::class.java.classLoader)

	private fun getClassLoader() = ResUtils::class.java.classLoader

	fun getResourceAsBytes(fileName: String): ByteArray {
		return getResourceAsStream(fileName).readAllBytes()
	}

	fun getResourceAsStream(fileName: String): InputStream {
		val inputStream = getClassLoader()?.getResourceAsStream(fileName)

		// the stream holding the file content
		return inputStream ?: throw IllegalArgumentException("file not found! $fileName")
	}

	fun getTranslatedLocales(): List<Locale> {
		val regex = Regex(".*Messages_([a-zA-Z]{2}).properties$")
		val localeFiles = resource.list("i18n")
		return if (localeFiles != null) {
			localeFiles
				.mapNotNull { file ->
					val result = regex.matchEntire(file)
					result?.groups?.get(1)?.value
				}
				.map { Locale.Builder().setLanguageTag(it).build() }
		} else {
			error("No locale files found")
		}
	}
}