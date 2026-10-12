package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.base.markdown.countWords
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.invalidInput
import com.darkrockstudios.apps.hammer.operations.notFound
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

internal fun sceneOperations(): List<Operation<*, *>> = listOf(
	operation<ProjectInput, SceneTree>(
		name = "scene.tree",
		description = "A project's scenes and groups in story order, with word counts.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val service = project.scope.get<SceneEditorService>()
			val content = project.scope.get<SceneContentRepository>()
			SceneTree(service.getSceneTree().root.children.map { it.toSceneNode(content) })
		}
	},
	operation<ProjectItemInput, SceneText>(
		name = "scene.read",
		description = "A scene's markdown, including unsaved edits, with its outline, notes, and tags.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val service = project.scope.get<SceneEditorService>()
			val scene = project.requireScene(input.id)
			val markdown = if (scene.archived) {
				val path = service.resolveScenePathFromFilesystemIncludingArchived(scene.id)
					?: notFound("Scene ${scene.id} has no file")
				service.loadSceneMarkdownRaw(scene, path)
			} else {
				project.scope.get<SceneContentRepository>().getCurrentSceneContent(scene)
			}
			SceneText(
				id = scene.id,
				name = scene.name,
				archived = scene.archived,
				markdown = markdown,
				wordCount = countWords(markdown),
				meta = SceneMeta(service.loadSceneMetadata(scene.id)),
			)
		}
	},
	operation<SceneReadManyInput, ScenePage>(
		name = "scene.read.many",
		description = "Scenes in story order with their markdown, metadata, and the groups they sit in, a page of " +
			"about maxWords, counting outlines and notes, at a time: every scene, a group's, or those in ids. Archived scenes are left out. " +
			"Pass next as after for the following page; it is null on the last.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		if (input.group != null && input.ids != null) invalidInput("Give group or ids, not both")
		if (input.maxWords < 1) invalidInput("maxWords must be at least 1")
		projects.withProject(input.project) { project ->
			val service = project.scope.get<SceneEditorService>()
			val content = project.scope.get<SceneContentRepository>()
			val placed = service.getSceneTree().root.children.flatMap { it.placedScenes(emptyList()) }
			val chosen = when {
				input.group != null -> {
					val group = service.getSceneItemFromId(input.group)
					if (group == null || group.type != SceneItem.Type.Group) notFound("No group ${input.group}")
					placed.filter { scene -> scene.groups.any { it.id == input.group } }
				}
				input.ids != null -> {
					val ids = input.ids.toSet()
					ids.forEach { id -> if (placed.none { it.scene.id == id }) notFound("No scene $id in the story") }
					placed.filter { it.scene.id in ids }
				}
				else -> placed
			}
			val start = if (input.after == null) {
				0
			} else {
				val index = chosen.indexOfFirst { it.scene.id == input.after }
				if (index < 0) invalidInput("Scene ${input.after} is not among these scenes")
				index + 1
			}

			val page = mutableListOf<PagedScene>()
			var words = 0
			var next = start
			while (next < chosen.size) {
				val (scene, groups) = chosen[next]
				val markdown = content.getCurrentSceneContent(scene)
				val meta = SceneMeta(service.loadSceneMetadata(scene.id))
				val count = countWords(markdown)
				val size = count + countWords(meta.outline) + countWords(meta.notes)
				// A page always holds at least one scene, however long.
				if (page.isNotEmpty() && words + size > input.maxWords) break
				page += PagedScene(
					id = scene.id,
					name = scene.name,
					path = groups.map { it.name },
					markdown = markdown,
					wordCount = count,
					meta = meta,
				)
				words += size
				next++
			}
			ScenePage(page, next = if (next < chosen.size) page.last().id else null)
		}
	},
	operation<ProjectInput, ArchivedScenes>(
		name = "scene.archived",
		description = "A project's archived scenes.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val archived = project.scope.get<SceneEditorService>().getArchivedScenes()
			ArchivedScenes(archived.map { ArchivedScene(it.id, it.name) }.sortedBy { it.id })
		}
	},
	operation<ProjectItemInput, SceneMeta>(
		name = "scene.meta.read",
		description = "A scene's outline, notes, and tags.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val scene = project.requireScene(input.id)
			SceneMeta(project.scope.get<SceneEditorService>().loadSceneMetadata(scene.id))
		}
	},
	operation<DraftListInput, Drafts>(
		name = "draft.list",
		description = "A scene's saved drafts, oldest first.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val scene = project.requireScene(input.sceneId)
			val drafts = project.scope.get<SceneDraftRepository>().findDrafts(scene.id)
			Drafts(drafts.map { DraftInfo(id = it.id, name = it.draftName, created = it.draftTimestamp) })
		}
	},
	operation<ProjectItemInput, DraftText>(
		name = "draft.read",
		description = "A draft's markdown.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val drafts = project.scope.get<SceneDraftRepository>()
			val draft = drafts.getDraftDef(input.id) ?: notFound("No draft ${input.id}")
			val markdown = drafts.loadDraftContent(draft) ?: notFound("Draft ${input.id} could not be read")
			DraftText(
				id = draft.id,
				sceneId = draft.sceneId,
				name = draft.draftName,
				created = draft.draftTimestamp,
				markdown = markdown,
			)
		}
	},
)

/** A live or archived scene; groups and the root are not scenes. */
internal fun OpenProject.requireScene(id: Int): SceneItem {
	val scene = scope.get<SceneEditorService>().getSceneItemFromIdIncludingArchived(id)
	if (scene == null || scene.type != SceneItem.Type.Scene) notFound("No scene $id")
	return scene
}

private fun TreeValue<SceneItem>.toSceneNode(content: SceneContentRepository): SceneNode {
	val scene = value
	return if (scene.type == SceneItem.Type.Scene) {
		SceneNode(
			id = scene.id,
			name = scene.name,
			kind = SceneKind.Scene,
			wordCount = countWords(content.getCurrentSceneContent(scene)),
			children = emptyList(),
		)
	} else {
		val children = children.map { it.toSceneNode(content) }
		SceneNode(
			id = scene.id,
			name = scene.name,
			kind = SceneKind.Group,
			wordCount = children.sumOf { it.wordCount },
			children = children,
		)
	}
}

private data class PlacedScene(val scene: SceneItem, val groups: List<SceneItem>)

private fun TreeValue<SceneItem>.placedScenes(groups: List<SceneItem>): List<PlacedScene> =
	if (value.type == SceneItem.Type.Scene) {
		listOf(PlacedScene(value, groups))
	} else {
		children.flatMap { it.placedScenes(groups + value) }
	}

@Serializable
data class SceneTree(val nodes: List<SceneNode>)

@Serializable
data class SceneNode(
	val id: Int,
	val name: String,
	val kind: SceneKind,
	/** Includes unsaved edits. A group's is the sum of its children's. */
	val wordCount: Int,
	val children: List<SceneNode>,
)

@Serializable
enum class SceneKind {
	@SerialName("scene") Scene,
	@SerialName("group") Group,
}

@Serializable
data class SceneText(
	val id: Int,
	val name: String,
	val archived: Boolean,
	val markdown: String,
	val wordCount: Int,
	val meta: SceneMeta,
)

@Serializable
data class SceneMeta(
	val outline: String,
	val notes: String,
	val tags: List<String>,
	val currentDraft: String,
	val created: Instant?,
	val lastEdited: Instant?,
) {
	constructor(metadata: SceneMetadata) : this(
		outline = metadata.outline,
		notes = metadata.notes,
		tags = metadata.tags.sorted(),
		currentDraft = metadata.currentDraftName,
		created = metadata.created,
		lastEdited = metadata.lastEdited,
	)
}

@Serializable
data class SceneReadManyInput(
	val project: String,
	/** A group whose scenes, at any depth, to read. */
	val group: Int? = null,
	val ids: List<Int>? = null,
	/** The previous page's next. */
	val after: Int? = null,
	val maxWords: Int = 12_000,
)

@Serializable
data class ScenePage(
	val scenes: List<PagedScene>,
	/** The last scene's id when more follow, to pass as after; null on the last page. */
	val next: Int?,
)

@Serializable
data class PagedScene(
	val id: Int,
	val name: String,
	/** Names of the groups containing the scene, outermost first. */
	val path: List<String>,
	val markdown: String,
	val wordCount: Int,
	val meta: SceneMeta,
)

@Serializable
data class ArchivedScenes(val scenes: List<ArchivedScene>)

@Serializable
data class ArchivedScene(val id: Int, val name: String)

@Serializable
data class DraftListInput(val project: String, val sceneId: Int)

@Serializable
data class Drafts(val drafts: List<DraftInfo>)

@Serializable
data class DraftInfo(val id: Int, val name: String, val created: Instant)

@Serializable
data class DraftText(
	val id: Int,
	val sceneId: Int,
	val name: String,
	val created: Instant,
	val markdown: String,
)
