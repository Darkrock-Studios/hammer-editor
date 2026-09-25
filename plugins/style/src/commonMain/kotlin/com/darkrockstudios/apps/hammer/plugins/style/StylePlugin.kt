package com.darkrockstudios.apps.hammer.plugins.style

import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OperationContext
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.core.ProjectInput
import com.darkrockstudios.apps.hammer.operations.core.ProjectItemInput
import com.darkrockstudios.apps.hammer.operations.core.SceneKind
import com.darkrockstudios.apps.hammer.operations.core.SceneNode
import com.darkrockstudios.apps.hammer.operations.core.SceneText
import com.darkrockstudios.apps.hammer.operations.core.SceneTree
import com.darkrockstudios.apps.hammer.operations.notFound
import com.darkrockstudios.apps.hammer.operations.operation
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import okio.FileSystem
import org.koin.mp.KoinPlatform.getKoin

/** Readability, adverbs, dialogue, and repetition, per scene and for the whole story. */
object StylePlugin : ClientPlugin {
	const val ID = "style"
	const val REPORT = "style.report"

	override val id = ID

	override fun operations(): List<Operation<*, *>> = listOf(
		operation<StyleReportInput, StyleReport>(
			name = REPORT,
			description = "A style report on a project's English prose: per scene and in total, readability, " +
				"adverbs, the share of dialogue, and repeated words and phrases.",
			access = Access.Read,
			agentVisible = true,
		) { input -> report(input) },
	)
}

/**
 * Built on scene.tree and scene.read, so it sees what any caller of those would, unsaved edits
 * included. The project is held open throughout, so those calls do not each open and close it.
 */
private suspend fun OperationContext.report(input: StyleReportInput): StyleReport =
	projects.withProject(input.project) { project -> report(input, StyleCache(project.def, getKoin().get<FileSystem>())) }

private suspend fun OperationContext.report(input: StyleReportInput, cache: StyleCache): StyleReport {
	val tree = operations.call<ProjectInput, SceneTree>("scene.tree", ProjectInput(input.project)).nodes
	val scenes = if (input.sceneIds.isEmpty()) {
		tree.flatMap { it.scenes() }
	} else {
		input.sceneIds.flatMap { id -> tree.findNode(id)?.scenes() ?: notFound("No scene or group $id") }.distinctBy { it.id }
	}

	val counted = scenes.map { scene ->
		val text = operations.call<ProjectItemInput, SceneText>("scene.read", ProjectItemInput(input.project, scene.id))
		scene to cache.counts(scene.id, text.markdown)
	}
	if (input.sceneIds.isEmpty()) cache.retainOnly(scenes.map { it.id }.toSet())

	return StyleReport(
		scenes = counted.map { (scene, counts) ->
			SceneStyle(scene.id, scene.name, StyleFigures.of(counts, SCENE_REPEAT_THRESHOLD, SCENE_LIST_SIZE))
		},
		total = StyleFigures.of(
			counted.fold(SceneCounts.EMPTY) { total, (_, counts) -> total + counts },
			TOTAL_REPEAT_THRESHOLD,
			TOTAL_LIST_SIZE,
		),
	)
}

private fun SceneNode.scenes(): List<SceneNode> =
	if (kind == SceneKind.Scene) listOf(this) else children.flatMap { it.scenes() }

private fun List<SceneNode>.findNode(id: Int): SceneNode? =
	firstNotNullOfOrNull { if (it.id == id) it else it.children.findNode(id) }

private const val SCENE_REPEAT_THRESHOLD = 3
private const val SCENE_LIST_SIZE = 10
private const val TOTAL_REPEAT_THRESHOLD = 5
private const val TOTAL_LIST_SIZE = 20
