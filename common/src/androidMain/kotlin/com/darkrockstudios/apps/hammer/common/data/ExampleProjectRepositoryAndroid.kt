package com.darkrockstudios.apps.hammer.common.data

import android.content.Context
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import io.github.aakira.napier.Napier
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.*
import java.util.function.BiConsumer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

actual val exampleProjectModule = module {
	singleOf(::ExampleProjectRepositoryAndroid) bind ExampleProjectRepository::class
}

class ExampleProjectRepositoryAndroid(
    globalSettingsRepository: GlobalSettingsRepository,
    private val context: Context
) : ExampleProjectRepository(globalSettingsRepository) {

	private fun loadExampleProjectZip(platform: Any?): ByteArray {
		with(platform as Context) {
			val resourceId = resources.getIdentifier(
				EXAMPLE_PROJECT_FILE_NAME.substringBefore("."), "raw", packageName
			)
			return resources.openRawResource(resourceId)
				.readBytes()
		}
	}

	override fun removeExampleProject() {
		val projectFile = File(projectsDir(), PROJECT_NAME)
		projectFile.deleteRecursively()
	}

	override fun platformInstall() {
		val projectFile = File(projectsDir(), PROJECT_NAME)
		if (!projectFile.exists()) {
			Napier.i("Creating example project")

			val zipBytes = loadExampleProjectZip(context)
			val bais = ByteArrayInputStream(zipBytes)
			ZipInputStream(bais).use { zip ->
				forEachZipEntryRecursive(zip) { zipEntry, subZipStream ->
					val outFile = File(projectsDir(), zipEntry.name)
					Napier.d(outFile.toPath().toString())
					try {
						if (!zipEntry.isDirectory) {
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
}