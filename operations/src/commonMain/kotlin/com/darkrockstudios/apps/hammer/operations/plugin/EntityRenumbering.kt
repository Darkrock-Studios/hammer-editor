package com.darkrockstudios.apps.hammer.operations.plugin

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogRef
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Sync gave an entity of the project [projectId], made on this device, a new id: [oldId] became
 * [newId]. The old id now names an item the server had, so anything still holding it points at that.
 */
data class EntityRenumbering(val projectId: ProjectId, val type: EntityType, val oldId: Int, val newId: Int) {
	/** Where an action run from the entity is run from; a draft is no place. */
	val place: ActionPlace? = when (type) {
		EntityType.Scene -> ActionPlace.Scene
		EntityType.Note -> ActionPlace.Note
		EntityType.EncyclopediaEntry -> ActionPlace.Entry
		EntityType.TimelineEvent -> ActionPlace.Event
		EntityType.SceneDraft -> null
	}

	/** The kind an entity field picks the entity as; a draft cannot be picked. */
	val kind: CatalogKind? = when (type) {
		EntityType.Scene -> CatalogKind.Scene
		EntityType.Note -> CatalogKind.Note
		EntityType.EncyclopediaEntry -> CatalogKind.Entry
		EntityType.TimelineEvent -> CatalogKind.Event
		EntityType.SceneDraft -> null
	}

	/** The id of the item a run from [place] is about, the new one if it is the entity. */
	fun itemId(place: ActionPlace, itemId: Int?): Int? = if (place == this.place && itemId == oldId) newId else itemId

	/** [values] of [fields] with the entity picked by its new id: in scenes fields, and entity fields of its kind. */
	fun values(fields: List<ActionField>, values: JsonObject): JsonObject {
		val renumbered = fields.mapNotNull { field -> values[field.key]?.let { field.key to value(field, it) } }.toMap()
		return JsonObject(values + renumbered)
	}

	/** [page] with its fields' defaults renumbered. Its state is the plugin's, which the host cannot read, so it is left. */
	fun page(page: ActionPage): ActionPage = ActionPage(
		title = page.title,
		markdown = page.markdown,
		fields = page.fields.map(::field),
		buttons = page.buttons,
		state = page.state,
	)

	/**
	 * Whether [state] may hold the entity by its old id: has a number equal to it, or a string or key
	 * with it as a whole number in it. It may be a coincidence, but then the run is only shown as
	 * entered rather than carried on.
	 */
	fun mentioned(state: JsonElement?): Boolean = when (state) {
		null, JsonNull -> false
		is JsonObject -> state.keys.any(::mentionedIn) || state.values.any(::mentioned)
		is JsonArray -> state.any(::mentioned)
		is JsonPrimitive -> if (state.isString) mentionedIn(state.content) else state.content.toDoubleOrNull() == oldId.toDouble()
	}

	private fun mentionedIn(text: String) = WHOLE_NUMBER.findAll(text).any { it.value.toIntOrNull() == oldId }

	private fun value(field: ActionField, value: JsonElement): JsonElement = when (field) {
		is ActionField.Scenes -> if (type == EntityType.Scene) sceneIds(value) else value
		is ActionField.Entity -> kind?.let { refs(value, it) } ?: value
		is ActionField.Setting, is ActionField.Text -> value
	}

	private fun sceneIds(value: JsonElement): JsonElement = when (value) {
		is JsonArray -> JsonArray(value.map(::sceneIds))
		is JsonPrimitive -> if (!value.isString && value.intOrNull == oldId) JsonPrimitive(newId) else value
		is JsonObject -> value
	}

	private fun refs(value: JsonElement, kind: CatalogKind): JsonElement = when (value) {
		is JsonArray -> JsonArray(value.map { refs(it, kind) })
		is JsonObject -> if (value.toCatalogRef() == CatalogRef(kind, oldId)) CatalogRef(kind, newId).toJson() else value
		is JsonPrimitive -> value
	}

	private fun field(field: ActionField): ActionField = when (field) {
		is ActionField.Scenes -> if (type != EntityType.Scene || oldId !in field.defaultIds) field else ActionField.Scenes(
			key = field.key,
			label = field.label,
			hint = field.hint,
			multiple = field.multiple,
			required = field.required,
			defaultIds = field.defaultIds.map { if (it == oldId) newId else it },
		)

		is ActionField.Entity -> {
			val old = kind?.let { CatalogRef(it, oldId) }
			if (old == null || old !in field.defaultRefs) field else ActionField.Entity(
				key = field.key,
				label = field.label,
				hint = field.hint,
				filter = field.filter,
				multiple = field.multiple,
				required = field.required,
				defaultRefs = field.defaultRefs.map { if (it == old) CatalogRef(old.kind, newId) else it },
			)
		}

		is ActionField.Setting, is ActionField.Text -> field
	}

	private companion object {
		val WHOLE_NUMBER = Regex("[0-9]+")
	}
}
