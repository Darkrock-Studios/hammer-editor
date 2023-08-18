package com.darkrockstudios.apps.hammer.utils

import java.io.File
import java.net.URL
import java.util.*

private fun getResourceFolderFiles(folder: String): List<File>? {
	val loader = Thread.currentThread().getContextClassLoader()
	val url: URL? = loader.getResource(folder)

	return if (url != null) {
		val path: String = url.path
		File(path).listFiles()?.toList()
	} else {
		null
	}
}

fun getTranslatedLocales(): List<Locale> {
	val regex = Regex("Messages_([a-zA-Z]{2}).properties")
	val localeFiles = getResourceFolderFiles("i18n")
	return if (localeFiles != null) {
		localeFiles
			.mapNotNull { file ->
				val result = regex.matchEntire(file.name)
				result?.groups?.get(1)?.value
			}
			.map { Locale.Builder().setLanguageTag(it).build() }
	} else {
		error("No locale files found")
	}
}