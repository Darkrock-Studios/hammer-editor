package com.darkrockstudios.apps.hammer.common.data.projectbackup

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipDirectory {
	@Throws(IOException::class)
	fun zipDirectory(directoryPath: String, zipFilePath: String) {
		val directory = File(directoryPath)
		val zipOut = ZipOutputStream(FileOutputStream(zipFilePath))
		zipFile(directory, directory.name, zipOut)
		zipOut.close()
	}

	@Throws(IOException::class)
	private fun zipFile(fileToZip: File, fileName: String, zipOut: ZipOutputStream) {
		if (fileToZip.isHidden) {
			return
		}
		if (fileToZip.isDirectory) {
			val children = fileToZip.listFiles()
			for (childFile in children) {
				zipFile(childFile, fileName + "/" + childFile.name, zipOut)
			}
			return
		}
		val fis = FileInputStream(fileToZip)
		val zipEntry = ZipEntry(fileName)
		zipOut.putNextEntry(zipEntry)
		val bytes = ByteArray(1024)
		var length: Int
		while (fis.read(bytes).also { length = it } >= 0) {
			zipOut.write(bytes, 0, length)
		}
		fis.close()
	}
}