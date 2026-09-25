package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.countWords
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationScope
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
