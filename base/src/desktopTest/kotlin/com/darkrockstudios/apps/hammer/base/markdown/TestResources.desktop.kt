package com.darkrockstudios.apps.hammer.base.markdown

actual fun readTestResource(name: String): String {
	val stream = checkNotNull(object {}.javaClass.getResourceAsStream("/$name")) { "Missing test resource $name" }
	return stream.use { it.readBytes().decodeToString() }
}
