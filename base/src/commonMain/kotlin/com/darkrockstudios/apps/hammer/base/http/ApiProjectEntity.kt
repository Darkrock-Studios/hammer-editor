package com.darkrockstudios.apps.hammer.base.http

import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
@Polymorphic
sealed interface ApiProjectEntity {
	val type: Type
	val id: Int

	/**
	 * Canonical hash for this entity. Implementations live in the same file as the
	 * field declarations so that adding a field forces the author to update the
	 * hash impl right below it. Defended structurally by [EntityHashSensitivityTest]
	 * which fails if any serialized field is silently absent from the digest.
	 */
	fun hash(): String

	@Serializable
	data class SceneEntity(
		override val type: Type = Type.SCENE,
		override val id: Int,
		val sceneType: ApiSceneType,
		val order: Int,
		val name: String,
		val path: List<Int>,
		val content: String = "",
		val outline: String = "",
		val notes: String = "",
		val archived: Boolean = false,
		val confirmedReferences: Set<Int> = emptySet(),
		val dismissedReferences: Set<Int> = emptySet(),
		val tags: Set<String> = emptySet(),
		val created: Instant? = null,
		val lastEdited: Instant? = null,
	) : ApiProjectEntity {
		override fun hash(): String = EntityHasher.hashScene(
			id = id,
			order = order,
			path = path,
			name = name,
			type = sceneType,
			content = content,
			outline = outline,
			notes = notes,
			archived = archived,
			confirmedReferences = confirmedReferences,
			dismissedReferences = dismissedReferences,
			tags = tags,
			created = created,
			lastEdited = lastEdited,
		)
	}

	@Serializable
	data class NoteEntity(
		override val type: Type = Type.NOTE,
		override val id: Int,
		val content: String,
		val created: Instant,
		val tags: Set<String> = emptySet(),
	) : ApiProjectEntity {
		override fun hash(): String = EntityHasher.hashNote(
			id = id,
			created = created,
			content = content,
			tags = tags,
		)
	}

	@Serializable
	data class TimelineEventEntity(
		override val type: Type = Type.TIMELINE_EVENT,
		override val id: Int,
		val order: Int,
		val date: String?,
		val content: String,
		val tags: Set<String> = emptySet(),
	) : ApiProjectEntity {
		override fun hash(): String = EntityHasher.hashTimelineEvent(
			id = id,
			order = order,
			content = content,
			date = date,
			tags = tags,
		)
	}

	@Serializable
	data class EncyclopediaEntryEntity(
		override val type: Type = Type.ENCYCLOPEDIA_ENTRY,
		override val id: Int,
		val name: String,
		val entryType: String,
		val text: String,
		val tags: Set<String>,
		val image: Image?,
		val aliases: List<String> = emptyList(),
	) : ApiProjectEntity {
		@Serializable
		data class Image(
			val base64: String,
			val fileExtension: String,
		)

		override fun hash(): String = EntityHasher.hashEncyclopediaEntry(
			id = id,
			name = name,
			entryType = entryType,
			text = text,
			tags = tags,
			image = image,
			aliases = aliases,
		)
	}

	@Serializable
	data class SceneDraftEntity(
		override val type: Type = Type.SCENE_DRAFT,
		override val id: Int,
		val sceneId: Int,
		val created: Instant,
		val name: String,
		val content: String
	) : ApiProjectEntity {
		override fun hash(): String = EntityHasher.hashSceneDraft(
			id = id,
			sceneId = sceneId,
			created = created,
			name = name,
			content = content,
		)
	}

	enum class Type(val id: Int) {
		SCENE(0),
		NOTE(1),
		TIMELINE_EVENT(2),
		ENCYCLOPEDIA_ENTRY(3),
		SCENE_DRAFT(4);

		fun toStringId() = when (this) {
			SCENE -> "scene"
			NOTE -> "note"
			TIMELINE_EVENT -> "timeline_event"
			ENCYCLOPEDIA_ENTRY -> "encyclopedia_entry"
			SCENE_DRAFT -> "scene_draft"
		}

		companion object {
			fun fromString(string: String?): Type? {
				return when (string?.trim()?.lowercase()) {
					"scene" -> SCENE
					"note" -> NOTE
					"timeline_event" -> TIMELINE_EVENT
					"encyclopedia_entry" -> ENCYCLOPEDIA_ENTRY
					"scene_draft" -> SCENE_DRAFT
					else -> null
				}
			}

			fun fromInt(id: Int?): Type? {
				return entries.find { it.id == id }
			}
		}
	}
}