package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.countWords
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
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
import kotlin.time.Instant

internal fun sceneOperations(): List<Operation<*, *>> = listOf(
	operation<ProjectInput, SceneTree>(
		name = "scene.tree",
		description = "A project's scenes and groups in story order, with word counts.",
		access = Access.Read,
		agentVisible = true,
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
		agentVisible = true,
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
	operation<SceneWriteInput, SceneWriteResult>(
		name = "scene.write",
		description = "Replace a scene's text with markdown. Mode draft saves it as a new draft of the scene and leaves " +
			"the scene alone. Mode live replaces the scene's text, saving the old text as a draft first.",
		access = Access.Write,
		agentVisible = true,
	) { input ->
		projects.withProject(input.project) { project ->
			val scene = project.requireEditableScene(input.id)
			val drafts = project.scope.get<SceneDraftRepository>()
			when (input.mode) {
				WriteMode.Draft -> {
					val name = input.draftName ?: DEFAULT_DRAFT_NAME
					val draft = drafts.saveDraft(scene, name, input.markdown)
						?: invalidInput("Could not save a draft named '$name'")
					SceneWriteResult(draftId = draft.id, previousDraftId = null, wordCount = countWords(input.markdown))
				}

				WriteMode.Live -> {
					if (input.draftName != null) invalidInput("draftName is for draft mode")
					project.requireNoUnrestoredEdits(scene)
					val service = project.scope.get<SceneEditorService>()
					val current = project.scope.get<SceneContentRepository>().getCurrentSceneContent(scene)
					val previous = when {
						current != input.markdown -> {
							val backup = drafts.saveDraft(scene, PREVIOUS_TEXT_DRAFT, current)
								?: error("Could not save scene ${scene.id}'s text as a draft")
							if (!service.replaceSceneText(scene, input.markdown)) {
								drafts.deleteDraft(backup.id)
								error("Could not save scene ${scene.id}")
							}
							backup.id
						}

						// The editor already holds this text; saving it is all that is left.
						service.hasDirtyBuffer(scene.id) -> {
							project.replaceText(scene, input.markdown)
							null
						}

						else -> null
					}
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
	operation<ProjectInput, ArchivedScenes>(
		name = "scene.archived",
		description = "A project's archived scenes.",
		access = Access.Read,
		agentVisible = true,
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
		agentVisible = true,
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
		agentVisible = true,
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
		agentVisible = true,
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
private fun OpenProject.requireScene(id: Int): SceneItem {
	val scene = scope.get<SceneEditorService>().getSceneItemFromIdIncludingArchived(id)
	if (scene == null || scene.type != SceneItem.Type.Scene) notFound("No scene $id")
	return scene
}

/** A scene whose text can change: not a group, and not archived. */
private fun OpenProject.requireEditableScene(id: Int): SceneItem {
	val scene = requireScene(id)
	if (scene.archived) invalidInput("Scene $id is archived. Unarchive it to change it.")
	return scene
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

/** Names the draft a live write saves the replaced text in. */
private const val PREVIOUS_TEXT_DRAFT = "Before external edit"

private const val DEFAULT_DRAFT_NAME = "Suggested edit"

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
