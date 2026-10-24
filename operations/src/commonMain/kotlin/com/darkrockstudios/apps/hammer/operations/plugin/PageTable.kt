package com.darkrockstudios.apps.hammer.operations.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** An [ActionPage] as JSON: as an action replies with it, and as a kept session holds it. */
@Serializable
class PageTable(
	val title: String? = null,
	val markdown: String? = null,
	val fields: List<FieldTable> = emptyList(),
	val buttons: List<ButtonTable> = emptyList(),
	val state: JsonElement? = null,
) {
	/** Throws [IllegalArgumentException] when a field cannot be shown, as [toActionFields] does. */
	fun toActionPage(): ActionPage = ActionPage(
		title = title?.ifBlank { null },
		markdown = markdown?.ifBlank { null },
		fields = fields.toActionFields(),
		buttons = buttons.map { ActionButton(it.id, it.label, it.primary, it.inline, it.validate) },
		state = state,
	)
}

@Serializable
class ButtonTable(
	val id: String,
	val label: String,
	val primary: Boolean = false,
	val inline: Boolean = false,
	val validate: Boolean = true,
)

fun ActionPage.toPageTable(): PageTable = PageTable(
	title = title,
	markdown = markdown,
	fields = fields.map { it.toFieldTable() },
	buttons = buttons.map { ButtonTable(it.id, it.label, it.primary, it.inline, it.validate) },
	state = state,
)
