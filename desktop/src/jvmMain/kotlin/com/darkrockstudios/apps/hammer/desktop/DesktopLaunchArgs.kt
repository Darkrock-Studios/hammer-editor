package com.darkrockstudios.apps.hammer.desktop

import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectDeepLink
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default

const val PROJECT_FLAG = "project"

data class DesktopLaunchArgs(
	val devMode: Boolean,
	val projectName: String?,
	val deepLink: ProjectDeepLink?,
)

fun parseDesktopLaunchArgs(args: Array<String>): DesktopLaunchArgs {
	val parser = ArgParser("hammer")

	val devMode by parser.option(
		ArgType.Boolean,
		shortName = "d",
		fullName = "dev",
		description = "Development Mode",
	).default(false)

	val projectName by parser.option(
		ArgType.String,
		shortName = "p",
		fullName = PROJECT_FLAG,
		description = "Open the named project directly, bypassing the project selection screen.",
	)

	val sceneId by parser.option(
		ArgType.Int,
		fullName = "scene",
		description = "After opening the project, jump to the scene with this id.",
	)

	val noteId by parser.option(
		ArgType.Int,
		fullName = "note",
		description = "After opening the project, jump to the note with this id.",
	)

	val entryId by parser.option(
		ArgType.Int,
		fullName = "entry",
		description = "After opening the project, jump to the encyclopedia entry with this id.",
	)

	val timelineEventId by parser.option(
		ArgType.Int,
		fullName = "timeline-event",
		description = "After opening the project, jump to the timeline event with this id.",
	)

	parser.parse(args)

	val targets = listOfNotNull(
		sceneId?.let { ProjectDeepLink.Scene(it) },
		noteId?.let { ProjectDeepLink.Note(it) },
		entryId?.let { ProjectDeepLink.EncyclopediaEntry(it) },
		timelineEventId?.let { ProjectDeepLink.TimelineEvent(it) },
	)
	require(targets.size <= 1) {
		"Only one of --scene, --note, --entry, --timeline-event may be set"
	}
	val deepLink = targets.firstOrNull()
	require(deepLink == null || projectName != null) {
		"--scene/--note/--entry/--timeline-event require --project"
	}

	return DesktopLaunchArgs(
		devMode = devMode,
		projectName = projectName,
		deepLink = deepLink,
	)
}
