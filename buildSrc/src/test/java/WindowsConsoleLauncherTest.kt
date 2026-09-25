package com.darkrockstudios.build

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WindowsConsoleLauncherTest {

	// The headers only: "MZ", the PE header's offset at 0x3C, then "PE\0\0" and the subsystem 92 bytes on.
	private fun exe(subsystem: Int): ByteArray {
		val bytes = ByteArray(0x200)
		val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
		bytes[0] = 'M'.code.toByte()
		bytes[1] = 'Z'.code.toByte()
		buffer.putInt(0x3C, 0x80)
		buffer.putInt(0x80, 0x00004550)
		buffer.putShort(0x80 + 92, subsystem.toShort())
		return bytes
	}

	private fun ByteArray.subsystem() = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).getShort(0x80 + 92).toInt()

	@Test
	fun `a GUI launcher becomes a console one and nothing else changes`() {
		val gui = exe(subsystem = 2)
		val console = withConsoleSubsystem(gui)

		assertEquals(3, console.subsystem())
		assertEquals(2, gui.subsystem())
		assertContentEquals(gui.copyOfRange(0, 0x80 + 92), console.copyOfRange(0, 0x80 + 92))
		assertContentEquals(gui.copyOfRange(0x80 + 94, gui.size), console.copyOfRange(0x80 + 94, console.size))
	}

	@Test
	fun `anything but a GUI or console executable is refused`() {
		assertFailsWith<IllegalArgumentException> { withConsoleSubsystem(ByteArray(0x200)) }
		assertFailsWith<IllegalArgumentException> { withConsoleSubsystem(exe(subsystem = 1)) }
	}

	@Test
	fun `the console launcher gets its own copy of the launcher's config`() {
		val image = Files.createTempDirectory("hammer").toFile()
		image.resolve("hammer.exe").writeBytes(exe(subsystem = 2))
		image.resolve("app").mkdirs()
		image.resolve("app/hammer.cfg").writeText("[Application]\napp.mainclass=Main\n")

		addWindowsConsoleLauncher(image, launcher = "hammer", name = "hammer-cli")

		assertEquals(3, image.resolve("hammer-cli.exe").readBytes().subsystem())
		assertEquals("[Application]\napp.mainclass=Main\n", image.resolve("app/hammer-cli.cfg").readText())
		image.deleteRecursively()
	}
}
