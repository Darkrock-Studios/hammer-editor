package com.darkrockstudios.apps.hammer.utilities

import java.io.File
import java.io.InputStream
import java.net.URISyntaxException
import java.net.URL


fun getFileFromResourceAsStream(fileName: String): InputStream {
	// The class loader that loaded the class
	val classLoader: ClassLoader = fileName::class.java.classLoader
	val inputStream = classLoader.getResourceAsStream(fileName)

	// the stream holding the file content
	return inputStream ?: throw IllegalArgumentException("file not found! $fileName")
}

@Throws(URISyntaxException::class)
fun getFileFromResource(fileName: String): File {
	val classLoader: ClassLoader = fileName::class.java.classLoader
	val resource: URL? = classLoader.getResource(fileName)
	return if (resource == null) {
		throw java.lang.IllegalArgumentException("file not found! $fileName")
	} else {
		// failed if files have whitespaces or special characters
		//return new File(resource.getFile());
		File(resource.toURI())
	}
}