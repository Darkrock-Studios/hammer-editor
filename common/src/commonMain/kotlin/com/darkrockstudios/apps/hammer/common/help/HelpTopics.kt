package com.darkrockstudios.apps.hammer.common.help

import com.darkrockstudios.apps.hammer.*
import org.jetbrains.compose.resources.StringResource

const val HELP_PLAYLIST_URL = "https://www.youtube.com/playlist?list=PLeEMLZzQczk8"

/** The groups of global help, in the order they are shown. */
enum class HelpSection(val title: StringResource) {
	GettingStarted(Res.string.help_section_getting_started),
	Writing(Res.string.help_section_writing),
	Worldbuilding(Res.string.help_section_worldbuilding),
	YourProject(Res.string.help_section_your_project),
	Syncing(Res.string.help_section_syncing),
	Sharing(Res.string.help_section_sharing),
	ExtendingHammer(Res.string.help_section_extending),
}

/** Every help topic, in display order within its [section]. */
enum class HelpTopic(
	val section: HelpSection,
	val title: StringResource,
	val blurb: StringResource,
	val videoId: String? = null,
) {
	WhatIsHammer(
		HelpSection.GettingStarted,
		Res.string.help_topic_what_is_hammer_title, Res.string.help_topic_what_is_hammer_blurb, "03c9iEHdVHw",
	),
	FirstProject(
		HelpSection.GettingStarted,
		Res.string.help_topic_first_project_title, Res.string.help_topic_first_project_blurb, "1noEd2QE_4Q",
	),
	ProjectGroups(
		HelpSection.GettingStarted,
		Res.string.help_topic_project_groups_title, Res.string.help_topic_project_groups_blurb,
	),
	StoryIdeas(
		HelpSection.GettingStarted,
		Res.string.help_topic_story_ideas_title, Res.string.help_topic_story_ideas_blurb,
	),
	WritingScenes(
		HelpSection.Writing,
		Res.string.help_topic_writing_scenes_title, Res.string.help_topic_writing_scenes_blurb, "hpZN74dAqFI",
	),
	Outlining(
		HelpSection.Writing,
		Res.string.help_topic_outlining_title, Res.string.help_topic_outlining_blurb, "gGfshqbTaew",
	),
	SceneDrafts(
		HelpSection.Writing,
		Res.string.help_topic_scene_drafts_title, Res.string.help_topic_scene_drafts_blurb, "rejtv653mSY",
	),
	FocusAndReaderMode(
		HelpSection.Writing,
		Res.string.help_topic_focus_reader_title, Res.string.help_topic_focus_reader_blurb,
	),
	Encyclopedia(
		HelpSection.Worldbuilding,
		Res.string.help_topic_encyclopedia_title, Res.string.help_topic_encyclopedia_blurb, "2wIxx8pYXlo",
	),
	RenameEverywhere(
		HelpSection.Worldbuilding,
		Res.string.help_topic_rename_everywhere_title, Res.string.help_topic_rename_everywhere_blurb,
	),
	Notes(
		HelpSection.Worldbuilding,
		Res.string.help_topic_notes_title, Res.string.help_topic_notes_blurb, "1HVm853lin4",
	),
	Timelines(
		HelpSection.Worldbuilding,
		Res.string.help_topic_timelines_title, Res.string.help_topic_timelines_blurb,
	),
	TrackingProgress(
		HelpSection.YourProject,
		Res.string.help_topic_tracking_progress_title, Res.string.help_topic_tracking_progress_blurb, "m5f_q5FClIA",
	),
	ImportExport(
		HelpSection.YourProject,
		Res.string.help_topic_import_export_title, Res.string.help_topic_import_export_blurb, "2-8dwmw-srk",
	),
	GlobalSearch(
		HelpSection.YourProject,
		Res.string.help_topic_global_search_title, Res.string.help_topic_global_search_blurb, "yrSuRmE3UvE",
	),
	Syncing(
		HelpSection.Syncing,
		Res.string.help_topic_syncing_title, Res.string.help_topic_syncing_blurb, "NO9B0ZSR1ic",
	),
	SyncConflicts(
		HelpSection.Syncing,
		Res.string.help_topic_sync_conflicts_title, Res.string.help_topic_sync_conflicts_blurb, "pSotPeWB68Y",
	),
	SharingStories(
		HelpSection.Sharing,
		Res.string.help_topic_sharing_stories_title, Res.string.help_topic_sharing_stories_blurb, "Wa-YrTmVuNE",
	),
	RequestingAnEditor(
		HelpSection.Sharing,
		Res.string.help_topic_requesting_editor_title, Res.string.help_topic_requesting_editor_blurb, "9_9My0_ndeA",
	),
	CoAuthoring(
		HelpSection.Sharing,
		Res.string.help_topic_coauthoring_title, Res.string.help_topic_coauthoring_blurb,
	),
	Plugins(
		HelpSection.ExtendingHammer,
		Res.string.help_topic_plugins_title, Res.string.help_topic_plugins_blurb,
	);

	val videoUrl: String? get() = videoId?.let { "https://www.youtube.com/watch?v=$it" }
}

/** The screens with a help button, and the topics each one shows. */
enum class HelpScreen(val topics: List<HelpTopic>) {
	ProjectHome(
		listOf(
			HelpTopic.TrackingProgress,
			HelpTopic.ImportExport,
			HelpTopic.GlobalSearch,
			HelpTopic.SharingStories,
			HelpTopic.CoAuthoring,
		)
	),
	Scenes(
		listOf(
			HelpTopic.WritingScenes,
			HelpTopic.Outlining,
			HelpTopic.SceneDrafts,
			HelpTopic.FocusAndReaderMode,
		)
	),
	Encyclopedia(listOf(HelpTopic.Encyclopedia, HelpTopic.RenameEverywhere)),
	Notes(listOf(HelpTopic.Notes)),
	Timeline(listOf(HelpTopic.Timelines)),
}

/** This section's topics, in display order. */
val HelpSection.topics: List<HelpTopic> get() = HelpTopic.entries.filter { it.section == this }
