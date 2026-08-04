package data.search

import com.darkrockstudios.apps.hammer.common.data.search.MarkdownProjector
import com.darkrockstudios.apps.hammer.common.util.readUtf8Into
import com.sun.management.ThreadMXBean
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.lang.management.ManagementFactory
import kotlin.io.path.createTempDirectory
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals

/** End-to-end scan cost from disk: a string per scene against decoding into the scan buffer. */
class ScanLoadPathBenchmark {

	private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

	private inline fun measureAlloc(block: () -> Unit): Long {
		val before = threads.getCurrentThreadAllocatedBytes()
		block()
		return threads.getCurrentThreadAllocatedBytes() - before
	}

	@Test
	fun `both load paths read the same text`() {
		val dir = createTempDirectory("scan-load-agree")
		val fs = FileSystem.SYSTEM
		val document = "The **big** dog barked at the well\\-known café 😀 日本語"
		val path = dir.resolve("scene.md").toOkioPath()
		fs.write(path) { writeUtf8(document) }

		val viaString = fs.read(path) { readUtf8() }
		assertEquals(document, viaString)

		val projector = MarkdownProjector()
		val byteCount = fs.metadata(path).size!!.toInt()
		val chars = fs.read(path) { readUtf8Into(projector, byteCount) }
		projector.projectSource(chars)

		val expected = MarkdownProjector().also { it.project(document) }.projected()
		assertEquals(expected, projector.projected())

		fs.deleteRecursively(dir.toOkioPath())
	}

	@Test
	fun `full-project scan from disk`() {
		val dir = createTempDirectory("scan-load-bench")
		val fs = FileSystem.SYSTEM
		val corpus = buildCorpus(300_000)
		val paths = corpus.mapIndexed { i, text ->
			val p = dir.resolve("scene-$i.md").toOkioPath()
			fs.write(p) { writeUtf8(text) }
			p
		}
		val query = "gulls argue"
		val projector = MarkdownProjector()

		fun stringPerScene(): Int {
			var hits = 0
			paths.forEach { p ->
				val text = fs.read(p) { readUtf8() }
				if (legacyProjectMarkdownToPlainText(text).indexOf(query, ignoreCase = true) >= 0) hits++
			}
			return hits
		}

		fun intoScanBuffer(): Int {
			var hits = 0
			paths.forEach { p ->
				val byteCount = (fs.metadata(p).size ?: 0L).toInt()
				val chars = fs.read(p) { readUtf8Into(projector, byteCount) }
				projector.projectSource(chars)
				if (projector.indexOf(query) >= 0) hits++
			}
			return hits
		}

		repeat(3) { stringPerScene(); intoScanBuffer() }
		assertEquals(stringPerScene(), intoScanBuffer())

		val oldTime = (0 until 3).minOf { measureTimeMillis { stringPerScene() } }
		val newTime = (0 until 3).minOf { measureTimeMillis { intoScanBuffer() } }
		val oldAlloc = measureAlloc { stringPerScene() }
		val newAlloc = measureAlloc { intoScanBuffer() }

		println("=== 300k words, ${paths.size} scenes, read from disk ===")
		println("  string per scene  : ${oldTime}ms, ${oldAlloc.mb()}")
		println("  into scan buffer  : ${newTime}ms, ${newAlloc.mb()}")

		fs.deleteRecursively(dir.toOkioPath())
	}

	private fun Long.mb(): String =
		"${(this / 1024.0 / 1024.0).let { kotlin.math.round(it * 100) / 100 }} MB"

	private fun buildCorpus(totalWords: Int): List<String> {
		val wordsPerScene = 2_000
		val scenes = totalWords / wordsPerScene
		val words = ("the quick brown fox jumps over a lazy dog while morning light spills across " +
			"the harbour and gulls argue about nothing at all she thought of him again").split(" ")
		return (0 until scenes).map { s ->
			val sb = StringBuilder()
			sb.append("# Scene $s\n\n")
			var w = 0
			while (w < wordsPerScene) {
				val paragraph = StringBuilder()
				repeat(120) {
					val word = words[(w * 7 + it * 13 + s) % words.size]
					if ((w + it) % 5 == 0) {
						when ((w + it) % 15) {
							0 -> paragraph.append("**").append(word).append("**")
							5 -> paragraph.append("*").append(word).append("*")
							else -> paragraph.append("well\\-known")
						}
					} else {
						paragraph.append(word)
					}
					paragraph.append(' ')
				}
				w += 120
				sb.append(paragraph.trimEnd()).append(".\n\n")
			}
			sb.toString()
		}
	}
}
