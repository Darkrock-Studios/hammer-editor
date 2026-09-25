import com.darkrockstudios.apps.hammer.base.DistributionChannel
import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.compose.plugin.CliPath
import com.darkrockstudios.apps.hammer.common.compose.plugin.CliPathInstaller
import com.darkrockstudios.apps.hammer.common.compose.plugin.CliPathState
import com.darkrockstudios.apps.hammer.common.compose.plugin.WindowsUserPath
import com.darkrockstudios.apps.hammer.common.compose.plugin.cliLauncher
import com.darkrockstudios.apps.hammer.common.compose.plugin.manualCommand
import com.darkrockstudios.apps.hammer.common.compose.plugin.withPathEntry
import com.darkrockstudios.apps.hammer.common.compose.plugin.withoutPathEntry
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
		channel: DistributionChannel = DistributionChannel.GITHUB,
	) = CliPath.detect(os, env, appPath, appStore, channel, home)

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
		assertEquals(
			CliPath.Script(home.resolve(".local/bin/hammer"), listOf("/home/a/Apps/hammer.AppImage"), needsAdmin = false),
			detect(env = mapOf("APPIMAGE" to "/home/a/Apps/hammer.AppImage"), appPath = "/tmp/.mount_hammerX/usr/bin/hammer"),
		)
	}

	@Test
	fun `on Windows a batch file runs the console launcher, except from the Store`() {
		val local = "C:\\Users\\a\\AppData\\Local"
		assertEquals(
			CliPath.Script(
				Paths.get(local, "Hammer", "bin", "hammer.cmd"),
				listOf("C:\\Program Files\\Hammer\\hammer-cli.exe"),
				needsAdmin = false,
				windows = true,
			),
			detect(HostOs.Windows, env = mapOf("LOCALAPPDATA" to local), appPath = "C:\\Program Files\\Hammer\\hammer.exe"),
		)
		assertEquals(
			CliPath.Provided("hammer"),
			detect(HostOs.Windows, appPath = "C:\\Program Files\\WindowsApps\\x\\hammer.exe", channel = DistributionChannel.MICROSOFT_STORE),
		)
		assertEquals(listOf("hammer"), cliLauncher(emptyMap(), "C:\\x\\hammer.exe", HostOs.Windows, DistributionChannel.MICROSOFT_STORE))
		assertEquals(
			"@echo off\r\nrem ${CliPath.MARKER}\r\n\"C:\\100%%\\hammer-cli.exe\" %*\r\n",
			CliPath.Script(Paths.get("x"), listOf("C:\\100%\\hammer-cli.exe"), needsAdmin = false, windows = true).content,
		)
	}

	@Test
	fun `a Windows script's folder is added to the user's PATH once, and removed with it`() {
		val userPath = object : WindowsUserPath {
			var value = "%USERPROFILE%\\bin;C:\\Tools"
			override fun read() = value
			override fun write(value: String) {
				this.value = value
			}
		}
		val installer = CliPathInstaller(userPath = userPath)
		val script = CliPath.Script(home.resolve("bin/hammer.cmd"), listOf("C:\\Hammer\\hammer-cli.exe"), needsAdmin = false, windows = true)
		val dir = script.file.parent.toString()

		installer.install(script)
		installer.install(script)
		assertEquals("%USERPROFILE%\\bin;C:\\Tools;$dir", userPath.value)

		installer.remove(script)
		assertEquals("%USERPROFILE%\\bin;C:\\Tools", userPath.value)
		assertFalse(Files.exists(script.file))
	}

	@Test
	fun `PATH entries match whatever their case or trailing backslash`() {
		assertEquals(null, withPathEntry("C:\\Tools;c:\\hammer\\bin\\", "C:\\Hammer\\bin"))
		assertEquals("C:\\Tools;C:\\Hammer\\bin", withPathEntry("C:\\Tools;", "C:\\Hammer\\bin"))
		assertEquals("C:\\Hammer\\bin", withPathEntry("", "C:\\Hammer\\bin"))
		assertEquals("C:\\Tools", withoutPathEntry("C:\\Tools;C:\\HAMMER\\bin", "C:\\Hammer\\bin"))
		assertEquals(null, withoutPathEntry("C:\\Tools", "C:\\Hammer\\bin"))
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
