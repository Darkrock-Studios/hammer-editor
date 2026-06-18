package com.darkrockstudios.apps.hammer.desktop

import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectDeepLink
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlin.system.exitProcess

const val PROJECT_FLAG = "project"

data class DesktopLaunchArgs(
	val devMode: Boolean,
	val projectName: String?,
	val deepLink: ProjectDeepLink?,
)

private class DesktopArgsCommand : CliktCommand(name = "hammer") {
	val devMode by option("-d", "--dev", help = "Development Mode").flag()

	val projectName by option(
		"-p", "--$PROJECT_FLAG",
		help = "Open the named project directly, bypassing the project selection screen.",
	)

	val deepLink: ProjectDeepLink? by mutuallyExclusiveOptions(
		option("--scene", help = "After opening the project, jump to the scene with this id.")
			.int().convert { ProjectDeepLink.Scene(it) },
		option("--note", help = "After opening the project, jump to the note with this id.")
			.int().convert { ProjectDeepLink.Note(it) },
		option(
			"--entry",
			help = "After opening the project, jump to the encyclopedia entry with this id."
		)
			.int().convert { ProjectDeepLink.EncyclopediaEntry(it) },
		option(
			"--timeline-event",
			help = "After opening the project, jump to the timeline event with this id."
		)
			.int().convert { ProjectDeepLink.TimelineEvent(it) },
	).single()

	override fun run() {
		if (deepLink != null && projectName == null) {
			throw UsageError("--scene/--note/--entry/--timeline-event require --project")
		}
	}
}

fun parseDesktopLaunchArgs(args: Array<String>): DesktopLaunchArgs {
	val command = DesktopArgsCommand()
	try {
		command.parse(args)
	} catch (e: PrintHelpMessage) {
		command.echoFormattedHelp(e)
		exitProcess(e.statusCode)
	}
	return DesktopLaunchArgs(
		devMode = command.devMode,
		projectName = command.projectName,
		deepLink = command.deepLink,
	)
}
