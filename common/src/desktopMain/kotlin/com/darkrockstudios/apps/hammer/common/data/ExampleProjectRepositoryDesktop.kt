package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.globalsettings.GlobalSettingsRepository
import io.github.aakira.napier.Napier
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.*
import java.util.function.BiConsumer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


actual val exampleProjectModule = module {
	singleOf(::ExampleProjectRepositoryDesktop) bind ExampleProjectRepository::class
}

class ExampleProjectRepositoryDesktop(
	globalSettingsRepository: GlobalSettingsRepository
) : ExampleProjectRepository(globalSettingsRepository) {

	private fun loadExampleProjectZip(): ByteArray {
		val path = "/raw/$EXAMPLE_PROJECT_FILE_NAME"
		this::class.java.getResourceAsStream(path).use { inputStream: InputStream? ->
			return inputStream?.readBytes() ?: error("Failed to read example project")
		}
	}

	override fun removeExampleProject() {
		val projectFile = File(projectsDir(), ExampleProjectRepository.PROJECT_NAME)
		projectFile.deleteRecursively()
	}

	override fun platformInstall() {
		val projectFile = File(projectsDir(), PROJECT_NAME)
		if (!projectFile.exists()) {
			Napier.i("Creating example project")

			val zipBytes = loadExampleProjectZip()
			val bais = ByteArrayInputStream(zipBytes)
			ZipInputStream(bais).use { zip ->
				forEachZipEntryRecursive(zip) { zipEntry, subZipStream ->
					val outFile = File(projectsDir(), zipEntry.name)
					Napier.d(outFile.toPath().toString())
					try {
						if (!zipEntry.isDirectory) {
							File(outFile.parent).apply {
								if (!exists()) {
									mkdirs()
								}
							}

							outFile.createNewFile()
							FileOutputStream(outFile).use { fis ->
								copyFile(inputStream = subZipStream, outputStream = fis)
							}
						} else {
							outFile.mkdirs()
						}
					} catch (e: IOException) {
						e.printStackTrace()
					}
				}
			}
		} else {
			Napier.i("Skipping example project creation")
		}
	}

	@Throws(IOException::class)
	private fun copyFile(inputStream: InputStream, outputStream: OutputStream) {
		val buffer = ByteArray(2048)
		var length: Int
		while (inputStream.read(buffer).also { length = it } > 0) {
			outputStream.write(buffer, 0, length)
		}
	}

	private fun projectsDir(): File {
		return File(globalSettingsRepository.globalSettings.projectsDirectory)
	}

	/**
	 * Recursively iterates through **all** [ZipEntry]s *(including entries of nested zip
	 * files)* of the given [ZipInputStream] passing the current zip entry and stream to
	 * the provided [BiConsumer].
	 */
	@Throws(IOException::class)
	private fun forEachZipEntryRecursive(
		zis: ZipInputStream,
		consumer: BiConsumer<ZipEntry, ZipInputStream>
	) {
		var entry: ZipEntry?
		do {
			entry = zis.nextEntry
			if (entry != null) {
				consumer.accept(entry, zis)
				val subZis = ZipInputStream(zis)
				forEachZipEntryRecursive(subZis, consumer)
			}
		} while (entry != null)
	}

	companion object {
		private const val PROJECT_NAME = "Alice In Wonderland"
	}
}