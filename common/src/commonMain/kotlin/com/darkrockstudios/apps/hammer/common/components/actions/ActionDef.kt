package com.darkrockstudios.apps.hammer.common.components.actions

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.action_category_editing
import com.darkrockstudios.apps.hammer.action_category_help
import com.darkrockstudios.apps.hammer.action_category_items
import com.darkrockstudios.apps.hammer.action_category_navigation
import com.darkrockstudios.apps.hammer.action_category_project
import com.darkrockstudios.apps.hammer.action_category_story
import org.jetbrains.compose.resources.StringResource
import kotlin.jvm.JvmInline

@JvmInline
value class ActionId(val value: String)

enum class ActionCategory(val label: StringResource) {
	Navigation(Res.string.action_category_navigation),
	Items(Res.string.action_category_items),
	Story(Res.string.action_category_story),
	Project(Res.string.action_category_project),
	Editing(Res.string.action_category_editing),
	Help(Res.string.action_category_help),
}

/** [Local] actions are shown with their chords but handled by the focused widget; a host never dispatches them. */
enum class ActionDispatch { Host, Local }

/** Ordinal is precedence: a higher layer answers before a lower one. */
enum class ActionLayer { Root, Module, Item, Overlay }

/** [global] actions still answer under an overlay barrier. */
data class ActionDef(
	val id: ActionId,
	val label: StringResource,
	val category: ActionCategory,
	val defaultChords: List<KeyChord>,
	val global: Boolean = false,
	val dispatch: ActionDispatch = ActionDispatch.Host,
)
