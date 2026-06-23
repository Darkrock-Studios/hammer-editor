package com.darkrockstudios.apps.hammer

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import okio.FileSystem
import okio.Path.Companion.toPath

class GenerateKeyringCommand : CliktCommand(name = "generate-keyring") {
	override fun help(context: Context) = "Generate a fresh server keyring (both roles, active v1)"

	private val out by option(
		"-o", "--out",
		help = "Write the keyring to this file instead of stdout",
	)

	override fun run() {
		val codec = cliKeyringCodec()
		val json = codec.serialize(codec.generate())
		val target = out
		if (target != null) {
			val path = target.toPath()
			path.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
			FileSystem.SYSTEM.write(path) { writeUtf8(json) }
			echo("Wrote keyring to $target")
		} else {
			echo(json)
		}
	}
}
