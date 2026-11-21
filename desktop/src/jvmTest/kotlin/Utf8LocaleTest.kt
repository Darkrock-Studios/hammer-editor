import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.hostOs
import com.darkrockstudios.apps.hammer.desktop.needsUtf8Relaunch
import com.darkrockstudios.apps.hammer.desktop.relaunchUnderUtf8LocaleIfNeeded
import com.darkrockstudios.apps.hammer.desktop.splitCmdline
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Utf8LocaleTest {
	@Test
	fun `Linux without a UTF-8 locale relaunches`() {
		assertTrue(needsUtf8Relaunch(HostOs.Linux, "ANSI_X3.4-1968", alreadyRelaunched = false))
	}

	@Test
	fun `Linux with a UTF-8 locale does not`() {
		assertFalse(needsUtf8Relaunch(HostOs.Linux, "UTF-8", alreadyRelaunched = false))
	}

	@Test
	fun `a relaunched process never relaunches again`() {
		assertFalse(needsUtf8Relaunch(HostOs.Linux, "ANSI_X3.4-1968", alreadyRelaunched = true))
	}

	@Test
	fun `other systems never relaunch`() {
		assertFalse(needsUtf8Relaunch(HostOs.Windows, "Cp1252", alreadyRelaunched = false))
		assertFalse(needsUtf8Relaunch(HostOs.MacOs, "US-ASCII", alreadyRelaunched = false))
	}

	@Test
	fun `cmdline splits on NULs and keeps empty arguments`() {
		val args = splitCmdline("java\u0000\u0000mcp\u0000".toByteArray())

		assertEquals(listOf("java", "", "mcp"), args.map { it.decodeToString() })
	}

	@Test
	fun `cmdline without a final NUL keeps its last argument`() {
		assertContentEquals(listOf("a", "b"), splitCmdline("a\u0000b".toByteArray()).map { it.decodeToString() })
	}

	@Test
	fun `a bare environment restarts with every argument intact`() {
		assumeTrue(hostOs == HostOs.Linux)
		// The arguments below have to leave this JVM intact to test anything.
		assumeTrue(System.getProperty("sun.jnu.encoding").equals("UTF-8", ignoreCase = true))
		// Longer than the one page ProcessHandle.Info reads, and non-ASCII, and empty.
		val args = listOf("x".repeat(5000), "Chapter 1꞉ Café", "")
		val java = File(System.getProperty("java.home"), "bin/java").path
		val command = listOf(java, "-cp", System.getProperty("java.class.path"), Utf8RelaunchProbe::class.java.name) + args

		val process = ProcessBuilder(command)
			.apply { environment().clear() }
			.redirectErrorStream(true)
			.start()
		val output = process.inputStream.bufferedReader(Charsets.UTF_8).readLines()
		assertTrue(process.waitFor(60, TimeUnit.SECONDS))

		assertEquals(listOf("encoding=UTF-8") + args.map { "arg=$it" }, output)
		assertEquals(Utf8RelaunchProbe.EXIT_CODE, process.exitValue())
	}
}

/** Run by the test above in a bare environment: relaunches, then reports what it ended up with. */
object Utf8RelaunchProbe {
	const val EXIT_CODE = 7

	@JvmStatic
	fun main(args: Array<String>) {
		relaunchUnderUtf8LocaleIfNeeded()
		val out = System.out.writer(Charsets.UTF_8)
		out.write("encoding=${System.getProperty("sun.jnu.encoding")}\n")
		args.forEach { out.write("arg=$it\n") }
		out.flush()
		exitProcess(EXIT_CODE)
	}
}
