package utils

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/**
 * The files of the zip [bytes] by name, in archive order. [charset] decodes a name only when the
 * entry does not flag it as UTF-8.
 */
fun unzip(bytes: ByteArray, charset: Charset = Charsets.UTF_8): Map<String, ByteArray> {
	val entries = linkedMapOf<String, ByteArray>()
	ZipInputStream(ByteArrayInputStream(bytes), charset).use { zip ->
		while (true) {
			val entry = zip.nextEntry ?: break
			if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
		}
	}
	return entries
}
