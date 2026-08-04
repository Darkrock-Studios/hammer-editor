package data.search

import com.darkrockstudios.apps.hammer.common.data.search.MarkdownProjector
import com.darkrockstudios.apps.hammer.common.data.search.projectMarkdownToPlainText
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals

/** Cost of a full-project scan: PR #821's implementation against the reusable workspace. */
class ProjectorBenchmark {

	private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

	private inline fun measureAlloc(block: () -> Unit): Long {
		val before = threads.getCurrentThreadAllocatedBytes()
		block()
		return threads.getCurrentThreadAllocatedBytes() - before
	}

	@Test
	fun `the rewrite agrees with the implementation it replaces`() {
		val corpus = buildCorpus(20_000)
		val projector = MarkdownProjector()
		corpus.forEach { scene ->
			projector.project(scene)
			assertEquals(legacyProjectMarkdownToPlainText(scene), projector.projected())
		}
	}

	@Test
	fun `full-project scan, legacy versus reusable workspace`() {
		for (words in listOf(300_000, 1_250_000)) {
			val corpus = buildCorpus(words)
			val query = "gulls argue"
			val projector = MarkdownProjector()

			repeat(3) {
				corpus.forEach { legacyProjectMarkdownToPlainText(it).indexOf(query, ignoreCase = true) }
				corpus.forEach { projectMarkdownToPlainText(it).indexOf(query, ignoreCase = true) }
				corpus.forEach { projector.project(it); projector.indexOf(query) }
			}

			val legacyTime = (0 until 3).minOf {
				measureTimeMillis {
					corpus.forEach { legacyProjectMarkdownToPlainText(it).indexOf(query, ignoreCase = true) }
				}
			}
			val wrapperTime = (0 until 3).minOf {
				measureTimeMillis {
					corpus.forEach { projectMarkdownToPlainText(it).indexOf(query, ignoreCase = true) }
				}
			}
			val reusedTime = (0 until 3).minOf {
				measureTimeMillis {
					corpus.forEach { projector.project(it); projector.indexOf(query) }
				}
			}

			val legacyAlloc = measureAlloc {
				corpus.forEach { legacyProjectMarkdownToPlainText(it).indexOf(query, ignoreCase = true) }
			}
			val wrapperAlloc = measureAlloc {
				corpus.forEach { projectMarkdownToPlainText(it).indexOf(query, ignoreCase = true) }
			}
			val reusedAlloc = measureAlloc {
				corpus.forEach { projector.project(it); projector.indexOf(query) }
			}

			println("=== ${words / 1000}k words, ${corpus.size} scenes ===")
			println("  legacy (#821)      : ${legacyTime}ms, ${legacyAlloc.mb()}")
			println("  new, workspace/call: ${wrapperTime}ms, ${wrapperAlloc.mb()}")
			println("  new, reused        : ${reusedTime}ms, ${reusedAlloc.mb()}")
		}
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
