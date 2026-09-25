package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.computeMoveRequest
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.movePositionCount
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.countWords
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.tagindex.cleanTags
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.FromStdin
import com.darkrockstudios.apps.hammer.operations.LiveEdit
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.invalidInput
import com.darkrockstudios.apps.hammer.operations.notFound
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal fun sceneWriteOperations(): List<Operation<*, *>> = listOf(
	operation<SceneWriteInput, SceneWriteResult>(
		name = "scene.write",
		description = "Replace a scene's text with markdown. Mode draft saves it as a new draft of the scene and leaves " +
			"the scene alone. Mode live replaces the scene's text, saving the old text as a draft first.",
		access = Access.Write,
		agentVisible = true,
	) { input ->
		projects.withProject(input.project) { project ->
			val scene = project.requireEditableScene(input.id)
			when (input.mode) {
				WriteMode.Draft -> {
					val name = input.draftName ?: DEFAULT_DRAFT_NAME
					val draft = project.scope.get<SceneDraftRepository>().saveDraft(scene, name, input.markdown)
						?: invalidInput("Could not save a draft named '$name'")
					SceneWriteResult(draftId = draft.id, previousDraftId = null, wordCount = countWords(input.markdown))
				}

				WriteMode.Live -> {
					if (input.draftName != null) invalidInput("draftName is for draft mode")
					val previous = project.liveReplace(scene, input.markdown)
					SceneWriteResult(draftId = null, previousDraftId = previous, wordCount = countWords(input.markdown))
				}
			}
		}
	},
	operation<SceneAppendInput, SceneWordCount>(
		name = "scene.append",
		description = "Add markdown to the end of a scene's text, as a new paragraph.",
		access = Access.Write,
		agentVisible = true,
	) { input ->
		if (input.markdown.isBlank()) invalidInput("Nothing to append")
		projects.withProject(input.project) { project ->
			val scene = project.requireEditableScene(input.id)
			project.requireNoUnrestoredEdits(scene)
			val current = project.scope.get<SceneContentRepository>().getCurrentSceneContent(scene).trimEnd()
			val added = input.markdown.trimStart('\n', '\r')
			val text = if (current.isEmpty()) added else "$current\n\n$added"
			project.replaceText(scene, text)
			SceneWordCount(countWords(text))
		}
	},
	operation<SceneCreateInput, SceneItemRef>(
		name = "scene.create",
		description = "Create an empty scene or group, at the end of its parent unless an index is given.",
		access = Access.Write,
		agentVisible = true,
	) { input ->
		projects.withProject(input.project) { project ->
			val service = project.scope.get<SceneEditorService>()
			val parent = project.requireParent(input.parentId)
			val siblings = service.getSceneTree().findBy { it.id == (parent?.id ?: SceneItem.ROOT_ID) }!!.children.size
			if (input.index != null && input.index !in 0..siblings) invalidInput("index must be from 0 to $siblings")

			val created = when (input.kind) {
				SceneKind.Scene -> service.createScene(parent, input.name)
				SceneKind.Group -> service.createGroup(parent, input.name)
			} ?: invalidInput("'${input.name}' is not a valid name")
			if (input.index != null && input.index != siblings) project.move(created, parent?.id ?: SceneItem.ROOT_ID, input.index)
			SceneItemRef(created)
		}
	},
	operation<SceneRenameInput, SceneItemRef>(
		name = "scene.rename",
		description = "Rename a scene or group.",
		access = Access.Write,
		agentVisible = true,
	) { input ->
		projects.withProject(input.project) { project ->
			val item = project.requireTreeItem(input.id)
			if (!project.scope.get<SceneEditorService>().renameScene(item, input.name)) {
				invalidInput("'${input.name}' is not a valid name")
			}
			SceneItemRef(item.copy(name = input.name.trim()))
		}
	},
	operation<SceneMoveInput, SceneItemRef>(
		name = "scene.move",
		description = "Move a scene or group to a position within a group, or the top level when parentId is left out. " +
			"index is its position there after the move.",
		access = Access.Write,
		agentVisible = true,
	) { input ->
		projects.withProject(input.project) { project ->
			val item = project.requireTreeItem(input.id)
			val parentId = project.requireParent(input.parentId)?.id ?: SceneItem.ROOT_ID
			project.move(item, parentId, input.index)
			SceneItemRef(item)
		}
	},
	operation<ProjectItemInput, SceneItemRef>(
		name = "scene.archive",
		description = "Archive a scene: take it out of the story, keeping its text, drafts, and notes.",
		access = Access.Write,
		agentVisible = true,
	) { input ->
		projects.withProject(input.project) { project ->
			val scene = project.requireEditableScene(input.id)
			project.requireNoUnrestoredEdits(scene)
			if (!project.scope.get<SceneEditorService>().archiveScene(scene)) error("Could not archive scene ${scene.id}")
			SceneItemRef(scene)
		}
	},
	operation<ProjectItemInput, SceneItemRef>(
		name = "scene.unarchive",
		description = "Return an archived scene to the story, at the end of the top level.",
		access = Access.Write,
		agentVisible = true,
	) { input ->
		projects.withProject(input.project) { project ->
			val scene = project.requireScene(input.id)
			if (!scene.archived) invalidInput("Scene ${scene.id} is not archived")
			val restored = project.scope.get<SceneEditorService>().unarchiveScene(scene)
				?: error("Could not unarchive scene ${scene.id}")
			SceneItemRef(restored)
		}
	},
	operation<ProjectItemInput, SceneItemRef>(
		name = "scene.delete",
		description = "Delete a scene, archived or not, or an empty group, for good.",
		access = Access.Destructive,
	) { input ->
		projects.withProject(input.project) { project ->
			val service = project.scope.get<SceneEditorService>()
			val item = service.getSceneItemFromIdIncludingArchived(input.id)?.takeUnless { it.isRootScene }
				?: notFound("No scene or group ${input.id}")
			val deleted = when (item.type) {
				SceneItem.Type.Scene -> service.deleteScene(item)
				else -> {
					if (service.getSceneTree().findBy { it.id == item.id }!!.children.isNotEmpty()) {
						invalidInput("Group ${item.id} is not empty")
					}
					service.deleteGroup(item)
				}
			}
			if (!deleted) error("Could not delete ${item.id}")
			SceneItemRef(item)
		}
	},
	operation<SceneMetaWriteInput, SceneMeta>(
		name = "scene.meta.write",
		description = "Change a scene's outline, notes, or tags. Fields left out keep their value.",
		access = Access.Write,
		agentVisible = true,
	) { input ->
		projects.withProject(input.project) { project ->
			val scene = project.requireScene(input.id)
			val service = project.scope.get<SceneEditorService>()
			val current = service.loadSceneMetadata(scene.id)
			val updated = current.copy(
				outline = input.outline ?: current.outline,
				notes = input.notes ?: current.notes,
				tags = input.tags?.let { cleanTags(it.toSet()) } ?: current.tags,
			)
			service.storeMetadata(updated, scene.id)
			SceneMeta(updated)
		}
	},
	operation<DraftCreateInput, DraftInfo>(
		name = "draft.create",
		description = "Save a scene's current text as a named draft.",
		access = Access.Write,
		agentVisible = true,
	) { input ->
		projects.withProject(input.project) { project ->
			val scene = project.requireEditableScene(input.sceneId)
			project.requireNoUnrestoredEdits(scene)
			val draft = project.scope.get<SceneDraftRepository>().saveDraft(scene, input.name)
				?: invalidInput("Could not save a draft named '${input.name}'")
			DraftInfo(id = draft.id, name = draft.draftName, created = draft.draftTimestamp)
		}
	},
	operation<DraftApplyInput, DraftApplied>(
		name = "draft.apply",
		description = "Replace a scene's text with one of its drafts, saving the replaced text as a draft first.",
		access = Access.Write,
		agentVisible = true,
	) { input ->
		projects.withProject(input.project) { project ->
			val drafts = project.scope.get<SceneDraftRepository>()
			val draft = drafts.getDraftDef(input.id) ?: notFound("No draft ${input.id}")
			val markdown = drafts.loadDraftContent(draft) ?: notFound("Draft ${input.id} could not be read")
			val scene = project.requireEditableScene(draft.sceneId)
			DraftApplied(sceneId = scene.id, previousDraftId = project.liveReplace(scene, markdown), wordCount = countWords(markdown))
		}
	},
	operation<ProjectItemInput, DraftInfo>(
		name = "draft.delete",
		description = "Delete a draft for good.",
		access = Access.Destructive,
	) { input ->
		projects.withProject(input.project) { project ->
			val drafts = project.scope.get<SceneDraftRepository>()
			val draft = drafts.getDraftDef(input.id) ?: notFound("No draft ${input.id}")
			if (!drafts.removeDraft(draft.id)) error("Could not delete draft ${draft.id}")
			DraftInfo(id = draft.id, name = draft.draftName, created = draft.draftTimestamp)
		}
	},
)

/** A scene whose text can change: not a group, and not archived. */
private fun OpenProject.requireEditableScene(id: Int): SceneItem {
	val scene = requireScene(id)
	if (scene.archived) invalidInput("Scene $id is archived. Unarchive it to change it.")
	return scene
}

/** A scene or group in the story: not archived, and not the root. */
private fun OpenProject.requireTreeItem(id: Int): SceneItem {
	val item = scope.get<SceneEditorService>().getSceneItemFromId(id)
	if (item == null || item.isRootScene) notFound("No scene or group $id")
	return item
}

/** The group [id] names, or null for the top level. */
private fun OpenProject.requireParent(id: Int?): SceneItem? {
	if (id == null || id == SceneItem.ROOT_ID) return null
	val parent = requireTreeItem(id)
	if (parent.type != SceneItem.Type.Group) invalidInput("$id is a scene, not a group")
	return parent
}

private suspend fun OpenProject.move(item: SceneItem, parentId: Int, index: Int) {
	val service = scope.get<SceneEditorService>()
	val tree = service.getSceneTree()
	val positions = movePositionCount(tree, item, parentId) ?: invalidInput("${item.id} cannot move into $parentId")
	if (index !in 0 until positions) invalidInput("index must be from 0 to ${positions - 1}")
	service.moveScene(computeMoveRequest(tree, item, parentId, index)!!)
}

/** Refuses to save over unsaved edits left by a session that did not close, which saving would discard. */
private fun OpenProject.requireNoUnrestoredEdits(scene: SceneItem) {
	if (scope.get<SceneEditorService>().hasUnrestoredEdits(scene)) {
		invalidInput("Scene ${scene.id} has unsaved edits from a session that did not close. Open the project in Hammer first.")
	}
}

private suspend fun OpenProject.replaceText(scene: SceneItem, markdown: String) {
	if (!scope.get<SceneEditorService>().replaceSceneText(scene, markdown)) error("Could not save scene ${scene.id}")
}

/**
 * Replaces [scene]'s text with [markdown], first saving the text it replaces, including an open
 * editor's unsaved edits, as a draft. Returns that draft's id, or null when the text was already [markdown].
 */
private suspend fun OpenProject.liveReplace(scene: SceneItem, markdown: String): Int? {
	requireNoUnrestoredEdits(scene)
	val service = scope.get<SceneEditorService>()
	val drafts = scope.get<SceneDraftRepository>()
	val current = scope.get<SceneContentRepository>().getCurrentSceneContent(scene)
	return when {
		current != markdown -> {
			val backup = drafts.saveDraft(scene, PREVIOUS_TEXT_DRAFT, current)
				?: error("Could not save scene ${scene.id}'s text as a draft")
			if (!service.replaceSceneText(scene, markdown)) {
				drafts.removeDraft(backup.id)
				error("Could not save scene ${scene.id}")
			}
			backup.id
		}

		// The editor already holds this text; saving it is all that is left.
		service.hasDirtyBuffer(scene.id) -> {
			replaceText(scene, markdown)
			null
		}

		else -> null
	}
}

/** Names the draft a live change saves the replaced text in. */
private const val PREVIOUS_TEXT_DRAFT = "Before external edit"

private const val DEFAULT_DRAFT_NAME = "Suggested edit"

@Serializable
enum class WriteMode {
	/** Saves the text as a new draft of the scene. */
	@SerialName("draft") Draft,

	/** Replaces the scene's text. */
	@LiveEdit
	@SerialName("live") Live,
}

@Serializable
data class SceneWriteInput(
	val project: String,
	val id: Int,
	val mode: WriteMode,
	@FromStdin val markdown: String,
	/** The new draft's name, in draft mode only. */
	val draftName: String? = null,
)

@Serializable
data class SceneWriteResult(
	/** The new draft, in draft mode. */
	val draftId: Int?,
	/** In live mode, the draft holding the text it replaced; null when the text was unchanged. */
	val previousDraftId: Int?,
	val wordCount: Int,
)

@LiveEdit
@Serializable
data class SceneAppendInput(
	val project: String,
	val id: Int,
	@FromStdin val markdown: String,
)

@Serializable
data class SceneWordCount(val wordCount: Int)

@Serializable
data class SceneCreateInput(
	val project: String,
	val name: String,
	val kind: SceneKind = SceneKind.Scene,
	/** The group to create it in; left out, the top level. */
	val parentId: Int? = null,
	/** Its position among its siblings; left out, the end. */
	val index: Int? = null,
)

@Serializable
data class SceneRenameInput(val project: String, val id: Int, val name: String)

@Serializable
data class SceneMoveInput(
	val project: String,
	val id: Int,
	/** The group to move into; left out, the top level. */
	val parentId: Int? = null,
	val index: Int,
)

@Serializable
data class SceneItemRef(val id: Int, val name: String, val kind: SceneKind) {
	constructor(item: SceneItem) : this(
		id = item.id,
		name = item.name,
		kind = if (item.type == SceneItem.Type.Scene) SceneKind.Scene else SceneKind.Group,
	)
}

@Serializable
data class SceneMetaWriteInput(
	val project: String,
	val id: Int,
	val outline: String? = null,
	val notes: String? = null,
	val tags: List<String>? = null,
)

@Serializable
data class DraftCreateInput(val project: String, val sceneId: Int, val name: String)

@LiveEdit
@Serializable
data class DraftApplyInput(val project: String, val id: Int)

@Serializable
data class DraftApplied(
	val sceneId: Int,
	/** The draft holding the text it replaced; null when the text was unchanged. */
	val previousDraftId: Int?,
	val wordCount: Int,
)
