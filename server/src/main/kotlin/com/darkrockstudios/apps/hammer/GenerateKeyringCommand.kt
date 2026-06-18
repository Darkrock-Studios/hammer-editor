package com.darkrockstudios.apps.hammer

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
class GenerateKeyringCommand : Subcommand(
	"generate-keyring",
	"Generate a fresh server keyring (both roles, active v1)",
) {
	private val out by option(
		ArgType.String, shortName = "o", fullName = "out",
		description = "Write the keyring to this file instead of stdout",
	)

	override fun execute() {
		val codec = cliKeyringCodec()
		val json = codec.serialize(codec.generate())
		val target = out
		if (target != null) {
			val path = target.toPath()
			path.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
			FileSystem.SYSTEM.write(path) { writeUtf8(json) }
			println("Wrote keyring to $target")
		} else {
			println(json)
		}
		exitProcess(0)
	}
}