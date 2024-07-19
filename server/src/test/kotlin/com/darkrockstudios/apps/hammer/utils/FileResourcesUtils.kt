package com.darkrockstudios.apps.hammer.utils

import okio.Path.Companion.toOkioPath
import okio.fakefilesystem.FakeFileSystem
import java.io.File
import java.io.IOException

object FileResourcesUtils {
	private fun getResourceFiles(clazz: Class<*>, path: String): List<File> {
		val files = mutableListOf<File>()

		val dirURL = clazz.classLoader.getResource(path)!!
		val file = File(dirURL.toURI())
		files.addAll(getResourceFiles(file))

		return files
	}

	private fun getResourceFiles(file: File): List<File> {
		val files = mutableListOf<File>()

		files.add(file)
		if (file.isDirectory) {
			val allFiles = file.listFiles()
			allFiles?.forEach { child ->
				files.addAll(getResourceFiles(child))
			}
		}

		return files
	}

	@Throws(IOException::class)
	fun copyResourceFolderToFakeFileSystem(
		from: okio.Path,
		to: okio.Path,
		ffs: FakeFileSystem,
		includeFromDir: Boolean = true
	) {
		val clazz = FileResourcesUtils::class.java
		val resFiles = getResourceFiles(clazz, from.toString())
			.filter { it.name != ".gitkeep" }

		val dirURL = clazz.classLoader.getResource(from.toString())!!
		val fromDir = File(dirURL.toURI())

		resFiles.forEach { sourceFile ->
			val relPath = sourceFile.toOkioPath().relativeTo(fromDir.toOkioPath())

			var targetPath = if (includeFromDir) {
				to / from
			} else {
				to
			}
			relPath.segments.forEach { segment ->
				targetPath /= segment
			}

			if (sourceFile.isDirectory) {
				ffs.createDirectories(targetPath)
			} else {
				ffs.createDirectories(targetPath.parent!!)

				sourceFile.bufferedReader().use { reader ->
					ffs.write(targetPath.normalized(), false) {
						writeUtf8(reader.readText())
					}
				}
			}
		}
	}
}