package com.darkrockstudios.apps.hammer.android

import android.content.Context
import com.darkrockstudios.apps.hammer.common.data.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.getRootDocumentDirectory
import io.github.aakira.napier.Napier
import java.io.*
import java.util.function.BiConsumer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ExampleProject {

    private val PROJECT_NAME = "Alice In Wonderland"
    fun install(context: Context) {
        val projectFile = File(projectsDir(), PROJECT_NAME)
        if (!projectFile.exists()) {
            Napier.i("Creating example project")
            ZipInputStream(context.resources.openRawResource(R.raw.alice_in_wonderland_zip)).use { zip ->
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
        val buffer = ByteArray(1024)
        var length: Int
        while (inputStream.read(buffer).also { length = it } > 0) {
            outputStream.write(buffer, 0, length)
        }
    }

    private fun projectsDir(): File {
        return File(File(getRootDocumentDirectory()), ProjectsRepository.PROJECTS_DIR)
    }

    /**
     * Iterates through all [ZipEntry]s of the given [ZipInputStream] and
     * passes the current zip entry and stream to the provided [BiConsumer], but does
     * **not** recursively parse entries of nested zip files.
     */
    @Throws(IOException::class)
    fun forEachZipEntry(zis: ZipInputStream, consumer: BiConsumer<ZipEntry, ZipInputStream>) {
        var entry: ZipEntry?
        do {
            entry = zis.nextEntry
            if (entry != null) {
                consumer.accept(entry, zis)
            }
        } while (entry != null)
    }

    /**
     * Recursively iterates through **all** [ZipEntry]s *(including entries of nested zip
     * files)* of the given [ZipInputStream] passing the current zip entry and stream to
     * the provided [BiConsumer].
     */
    @Throws(IOException::class)
    fun forEachZipEntryRecursive(
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