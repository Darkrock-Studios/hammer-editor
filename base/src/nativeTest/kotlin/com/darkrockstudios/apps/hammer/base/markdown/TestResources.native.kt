package com.darkrockstudios.apps.hammer.base.markdown

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

// Native test binaries have no classpath, so read the source tree, starting from the working directory.
actual fun readTestResource(name: String): String {
	val relative = "src/commonTest/resources/$name".toPath()
	var directory: Path? = FileSystem.SYSTEM.canonicalize(".".toPath())
	while (directory != null) {
		for (candidate in listOf(directory / relative, directory / "base" / relative)) {
			if (FileSystem.SYSTEM.exists(candidate)) {
				return FileSystem.SYSTEM.read(candidate) { readUtf8() }
			}
		}
		directory = directory.parent
	}
	error("Missing test resource $name")
}
