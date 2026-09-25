package com.darkrockstudios.build

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val PE_HEADER_POINTER = 0x3C
private const val PE_SIGNATURE = 0x00004550 // "PE\0\0"

// The optional header follows the 4-byte signature and the 20-byte file header; Subsystem sits at
// the same offset in PE32 and PE32+.
private const val SUBSYSTEM_OFFSET = 4 + 20 + 68
private const val SUBSYSTEM_GUI = 2
private const val SUBSYSTEM_CONSOLE = 3

/**
 * Adds a console launcher, [name]`.exe`, beside the GUI launcher [launcher] in a jpackage app image
 * at [appImage]. jpackage has one launcher per app here, and a GUI one never shows output in a
 * terminal. The copy differs only in its PE subsystem, which is what `editbin /SUBSYSTEM:CONSOLE`
 * changes, and gets its own copy of the launcher's `.cfg`, which jpackage finds by the exe's name.
 */
fun addWindowsConsoleLauncher(appImage: File, launcher: String, name: String) {
	val gui = appImage.resolve("$launcher.exe")
	appImage.resolve("$name.exe").writeBytes(withConsoleSubsystem(gui.readBytes()))
	appImage.resolve("app/$launcher.cfg").copyTo(appImage.resolve("app/$name.cfg"), overwrite = true)
}

/** [exe] with its PE subsystem set to console. */
fun withConsoleSubsystem(exe: ByteArray): ByteArray {
	val bytes = exe.copyOf()
	val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
	require(bytes.size > PE_HEADER_POINTER + 4 && bytes[0] == 'M'.code.toByte() && bytes[1] == 'Z'.code.toByte()) {
		"Not a Windows executable"
	}
	val pe = buffer.getInt(PE_HEADER_POINTER)
	require(pe > 0 && pe + SUBSYSTEM_OFFSET + 2 <= bytes.size && buffer.getInt(pe) == PE_SIGNATURE) { "No PE header" }
	val subsystem = buffer.getShort(pe + SUBSYSTEM_OFFSET).toInt()
	require(subsystem == SUBSYSTEM_GUI || subsystem == SUBSYSTEM_CONSOLE) { "Unexpected PE subsystem $subsystem" }
	buffer.putShort(pe + SUBSYSTEM_OFFSET, SUBSYSTEM_CONSOLE.toShort())
	return bytes
}
