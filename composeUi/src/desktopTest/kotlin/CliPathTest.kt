import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.compose.plugin.CliPath
import com.darkrockstudios.apps.hammer.common.compose.plugin.CliPathInstaller
import com.darkrockstudios.apps.hammer.common.compose.plugin.CliPathState
import com.darkrockstudios.apps.hammer.common.compose.plugin.manualCommand
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CliPathTest {

	private val home: Path = createTempDirectory("home")

	@After
	fun tearDown() {
		home.toFile().setWritable(true)
		home.toFile().walkBottomUp().forEach { it.setWritable(true); it.delete() }
	}

	private fun detect(
		os: HostOs = HostOs.Linux,
		env: Map<String, String> = emptyMap(),
		appPath: String? = "/opt/hammer/bin/hammer",
		appStore: Boolean = false,
	) = CliPath.detect(os, env, appPath, appStore, home)

	@Test
	fun `each package puts hammer on PATH its own way`() {
		assertEquals(CliPath.Provided("hammer-editor"), detect(env = mapOf("SNAP_NAME" to "hammer-editor")))
		assertEquals(
			CliPath.Script(home.resolve(".local/bin/hammer"), listOf("flatpak", "run", "studio.darkrock.hammer"), needsAdmin = false),
			detect(env = mapOf("FLATPAK_ID" to "studio.darkrock.hammer")),
		)
		assertEquals(
			CliPath.Script(home.resolve(".local/bin/hammer"), listOf("/opt/hammer/bin/hammer"), needsAdmin = false),
			detect(),
		)
		val mac = "/Applications/hammer.app/Contents/MacOS/hammer"
		assertEquals(CliPath.Script(Paths.get("/usr/local/bin/hammer"), listOf(mac), needsAdmin = true), detect(HostOs.MacOs, appPath = mac))
		assertTrue(detect(HostOs.MacOs, appPath = mac, appStore = true) is CliPath.Manual)
		assertEquals(CliPath.Unavailable, detect(appPath = null))
		assertEquals(CliPath.Unavailable, detect(HostOs.Windows, appPath = "C:\\Hammer\\hammer.exe"))
	}

	@Test
	fun `the script runs the launcher with every argument`() {
		val script = CliPath.Script(home.resolve("bin/hammer"), listOf("/bin/echo", "it's here"), needsAdmin = false)
		assertEquals("#!/bin/sh\n${CliPath.MARKER}\nexec /bin/echo 'it'\\''s here' \"\$@\"\n", script.content)

		CliPathInstaller().install(script)

		assertEquals("it's here scene list\n", run(script.file.toString(), "scene", "list"))
	}

	@Test
	fun `the manual command writes the same script`() {
		val script = CliPath.Script(home.resolve("my bin/hammer"), listOf("/opt/My Hammer/it's/hammer"), needsAdmin = false)

		run("/bin/sh", "-c", script.manualCommand())

		assertEquals(script.content, script.file.readText())
		assertEquals(CliPathState.Installed, CliPathInstaller().state(script))
	}

	@Test
	fun `only Hammer's own script is replaced or removed`() {
		val installer = CliPathInstaller()
		val script = CliPath.Script(home.resolve("bin/hammer"), listOf("/opt/hammer/bin/hammer"), needsAdmin = false)
		assertEquals(CliPathState.Absent, installer.state(script))

		installer.install(script.copy(launcher = listOf("/old/hammer")))
		assertEquals(CliPathState.Stale, installer.state(script))
		installer.install(script)
		assertEquals(CliPathState.Installed, installer.state(script))
		installer.remove(script)
		assertEquals(CliPathState.Absent, installer.state(script))

		script.file.writeText("#!/bin/sh\nexec something-else\n")
		assertEquals(CliPathState.Taken, installer.state(script))
		assertFailsWith<IOException> { installer.install(script) }
		installer.remove(script)
		assertEquals("#!/bin/sh\nexec something-else\n", script.file.readText())
	}

	@Test
	fun `a directory the user cannot write goes through an administrator`() {
		val commands = mutableListOf<String>()
		val dir = Files.createDirectories(home.resolve("system/bin"))
		Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-xr-xr-x"))
		val script = CliPath.Script(dir.resolve("hammer"), listOf("/opt/hammer/bin/hammer"), needsAdmin = true)

		CliPathInstaller(runAsAdmin = { commands += it; true }).install(script)

		assertTrue(commands.single().startsWith("mkdir -p $dir && cp "))
		assertTrue(commands.single().endsWith(" $dir/hammer && chmod 755 $dir/hammer"))
	}

	@Test
	fun `a directory the user cannot write fails without an administrator`() {
		val dir = Files.createDirectories(home.resolve("system/bin"))
		Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-xr-xr-x"))
		val script = CliPath.Script(dir.resolve("hammer"), listOf("/opt/hammer/bin/hammer"), needsAdmin = false)

		assertFailsWith<IOException> { CliPathInstaller(runAsAdmin = { error("not asked") }).install(script) }
		assertFalse(Files.exists(script.file))
	}

	@Test
	fun `on PATH means the script's directory is listed`() {
		val script = CliPath.Script(Paths.get("/home/a/.local/bin/hammer"), listOf("x"), needsAdmin = false)
		assertTrue(script.onPath("/usr/bin:/home/a/.local/bin"))
		assertFalse(script.onPath("/usr/bin:/home/a/bin"))
		assertFalse(script.onPath(null))
	}

	private fun run(vararg command: String): String {
		val process = ProcessBuilder(*command).redirectErrorStream(true).start()
		val output = process.inputStream.readAllBytes().decodeToString()
		assertEquals(0, process.waitFor(), output)
		return output
	}
}
