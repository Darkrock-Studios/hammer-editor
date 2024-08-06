package com.darkrockstudios.apps.hammer.utils

import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import okio.Path
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
		from: Path,
		to: Path,
		ffs: FakeFileSystem,
		filterBlackList: List<String> = listOf(".gitkeep", ".sql"),
		includeFromDir: Boolean = true
	) {
		val clazz = FileResourcesUtils::class.java
		val resFiles = getResourceFiles(clazz, from.toString())
			.filter { file -> !filterBlackList.any { exclude -> file.name.endsWith(exclude) } }

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

	suspend fun setupDatabase(from: Path, database: SqliteTestDatabase) {
		val clazz = FileResourcesUtils::class.java
		val dbFiles = getResourceFiles(clazz, from.toString())
			.filter { it.name.endsWith(".sql") }

		dbFiles.forEach { sqlFile ->
			sqlFile.bufferedReader().use { reader ->
				val databaseSql = reader.readText()
				databaseSql.split(";").filter { it.isNotBlank() }.map { it.trim() + ";" }.forEach {
					database.executeAsync(it)
				}
			}
		}
	}
}