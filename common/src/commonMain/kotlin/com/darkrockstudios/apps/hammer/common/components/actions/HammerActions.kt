package com.darkrockstudios.apps.hammer.common.components.actions

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.action_app_quit
import com.darkrockstudios.apps.hammer.action_edit_bold
import com.darkrockstudios.apps.hammer.action_edit_find
import com.darkrockstudios.apps.hammer.action_edit_insert_image
import com.darkrockstudios.apps.hammer.action_edit_italic
import com.darkrockstudios.apps.hammer.action_edit_save
import com.darkrockstudios.apps.hammer.action_edit_strikethrough
import com.darkrockstudios.apps.hammer.action_edit_text_bigger
import com.darkrockstudios.apps.hammer.action_edit_text_reset
import com.darkrockstudios.apps.hammer.action_edit_text_smaller
import com.darkrockstudios.apps.hammer.action_help_cheat_sheet
import com.darkrockstudios.apps.hammer.action_item_close
import com.darkrockstudios.apps.hammer.action_item_create
import com.darkrockstudios.apps.hammer.action_item_new
import com.darkrockstudios.apps.hammer.action_item_next
import com.darkrockstudios.apps.hammer.action_item_prev
import com.darkrockstudios.apps.hammer.action_nav_encyclopedia
import com.darkrockstudios.apps.hammer.action_nav_home
import com.darkrockstudios.apps.hammer.action_nav_notes
import com.darkrockstudios.apps.hammer.action_nav_story
import com.darkrockstudios.apps.hammer.action_nav_timeline
import com.darkrockstudios.apps.hammer.action_palette_open
import com.darkrockstudios.apps.hammer.action_pane_next
import com.darkrockstudios.apps.hammer.action_pane_prev
import com.darkrockstudios.apps.hammer.action_project_close
import com.darkrockstudios.apps.hammer.action_project_gallery
import com.darkrockstudios.apps.hammer.action_project_save_all
import com.darkrockstudios.apps.hammer.action_project_sync
import com.darkrockstudios.apps.hammer.action_search_global
import com.darkrockstudios.apps.hammer.action_story_focus_mode
import com.darkrockstudios.apps.hammer.action_story_new_draft
import com.darkrockstudios.apps.hammer.action_story_new_group
import com.darkrockstudios.apps.hammer.action_story_next_group
import com.darkrockstudios.apps.hammer.action_story_prev_group
import com.darkrockstudios.apps.hammer.action_story_reading_mode
import com.darkrockstudios.apps.hammer.action_story_toggle_outline
import com.darkrockstudios.apps.hammer.action_story_toggle_scene_info
import com.darkrockstudios.apps.hammer.action_story_view_drafts
import com.darkrockstudios.apps.hammer.action_timeline_new
import com.darkrockstudios.apps.hammer.common.components.actions.ActionCategory.Editing
import com.darkrockstudios.apps.hammer.common.components.actions.ActionCategory.Help
import com.darkrockstudios.apps.hammer.common.components.actions.ActionCategory.Items
import com.darkrockstudios.apps.hammer.common.components.actions.ActionCategory.Navigation
import com.darkrockstudios.apps.hammer.common.components.actions.ActionCategory.Project
import com.darkrockstudios.apps.hammer.common.components.actions.ActionCategory.Story
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord.Companion.bare
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord.Companion.mod
import org.jetbrains.compose.resources.StringResource

/** Every project action. Table order is the cheat sheet and palette order. */
object HammerActions {

	private val table = mutableListOf<ActionDef>()

	private fun action(
		id: String,
		label: StringResource,
		category: ActionCategory,
		vararg chords: KeyChord,
		global: Boolean = false,
		dispatch: ActionDispatch = ActionDispatch.Host,
	): ActionDef = ActionDef(ActionId(id), label, category, chords.toList(), global, dispatch).also { table += it }

	private fun local(id: String, label: StringResource, vararg chords: KeyChord) =
		action(id, label, Editing, *chords, dispatch = ActionDispatch.Local)

	val NavHome = action("nav.home", Res.string.action_nav_home, Navigation, mod(ChordKey.Digit1))
	val NavStory = action("nav.story", Res.string.action_nav_story, Navigation, mod(ChordKey.Digit2))
	val NavNotes = action("nav.notes", Res.string.action_nav_notes, Navigation, mod(ChordKey.Digit3))
	val NavEncyclopedia = action("nav.encyclopedia", Res.string.action_nav_encyclopedia, Navigation, mod(ChordKey.Digit4))
	val NavTimeline = action("nav.timeline", Res.string.action_nav_timeline, Navigation, mod(ChordKey.Digit5))
	val PaneNext = action("pane.next", Res.string.action_pane_next, Navigation, bare(ChordKey.F6))
	val PanePrev = action("pane.prev", Res.string.action_pane_prev, Navigation, bare(ChordKey.F6, shift = true))

	val ItemNew = action("item.new", Res.string.action_item_new, Items, mod(ChordKey.N))
	val ItemNext = action("item.next", Res.string.action_item_next, Items, mod(ChordKey.PageDown))
	val ItemPrev = action("item.prev", Res.string.action_item_prev, Items, mod(ChordKey.PageUp))
	val ItemClose = action("item.close", Res.string.action_item_close, Items, mod(ChordKey.W))
	val ItemCreate = action("item.create", Res.string.action_item_create, Items, mod(ChordKey.Enter))
	val TimelineNew = action("timeline.new", Res.string.action_timeline_new, Items)

	val StoryNewGroup = action("story.newGroup", Res.string.action_story_new_group, Story, mod(ChordKey.N, shift = true))
	val StoryNextGroup =
		action("story.nextGroup", Res.string.action_story_next_group, Story, mod(ChordKey.PageDown, shift = true))
	val StoryPrevGroup =
		action("story.prevGroup", Res.string.action_story_prev_group, Story, mod(ChordKey.PageUp, shift = true))
	val StoryToggleOutline =
		action("story.toggleOutline", Res.string.action_story_toggle_outline, Story, mod(ChordKey.O, shift = true))
	val StoryToggleSceneInfo =
		action("story.toggleSceneInfo", Res.string.action_story_toggle_scene_info, Story, mod(ChordKey.M, shift = true))
	val StoryFocusMode =
		action("story.focusMode", Res.string.action_story_focus_mode, Story, mod(ChordKey.Enter, shift = true))
	val StoryReadingMode =
		action("story.readingMode", Res.string.action_story_reading_mode, Story, mod(ChordKey.R, shift = true))
	val StoryNewDraft = action("story.newDraft", Res.string.action_story_new_draft, Story, mod(ChordKey.D, shift = true))
	val StoryViewDrafts =
		action("story.viewDrafts", Res.string.action_story_view_drafts, Story, mod(ChordKey.H, shift = true))

	val ProjectClose = action("project.close", Res.string.action_project_close, Project, mod(ChordKey.W, shift = true))
	val ProjectSaveAll =
		action("project.saveAll", Res.string.action_project_save_all, Project, mod(ChordKey.S, shift = true), global = true)
	val ProjectSync = action("project.sync", Res.string.action_project_sync, Project, bare(ChordKey.F3), global = true)
	val ProjectGallery = action("project.gallery", Res.string.action_project_gallery, Project)
	val SearchGlobal = action("search.global", Res.string.action_search_global, Project, mod(ChordKey.F, shift = true))
	val PaletteOpen = action("palette.open", Res.string.action_palette_open, Help, mod(ChordKey.K), global = true)
	val HelpCheatSheet = action("help.cheatSheet", Res.string.action_help_cheat_sheet, Help, bare(ChordKey.F1), global = true)
	val AppQuit = action("app.quit", Res.string.action_app_quit, Project, mod(ChordKey.Q), global = true)

	val EditSave = local("edit.save", Res.string.action_edit_save, mod(ChordKey.S))
	val EditFind = local("edit.find", Res.string.action_edit_find, mod(ChordKey.F))
	val EditBold = local("edit.bold", Res.string.action_edit_bold, mod(ChordKey.B))
	val EditItalic = local("edit.italic", Res.string.action_edit_italic, mod(ChordKey.I))
	val EditStrikethrough = local("edit.strikethrough", Res.string.action_edit_strikethrough, mod(ChordKey.X, shift = true))
	val EditInsertImage = local("edit.insertImage", Res.string.action_edit_insert_image, mod(ChordKey.I, shift = true))
	val EditTextBigger = local(
		"edit.textBigger",
		Res.string.action_edit_text_bigger,
		mod(ChordKey.Equals),
		mod(ChordKey.Equals, shift = true),
	)
	val EditTextSmaller = local("edit.textSmaller", Res.string.action_edit_text_smaller, mod(ChordKey.Minus))
	val EditTextReset = local("edit.textReset", Res.string.action_edit_text_reset, mod(ChordKey.Digit0))

	val all: List<ActionDef> = table.toList()

	private val byIdMap: Map<ActionId, ActionDef> = all.associateBy { it.id }

	fun byId(id: ActionId): ActionDef? = byIdMap[id]
}
