package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

class StoryExportService(
	private val projectEntityDatasource: ProjectEntityDatasource,
) {
	private val markdownFlavour = CommonMarkFlavourDescriptor()
	private val markdownParser = MarkdownParser(markdownFlavour)

	suspend fun exportStoryAsHtml(
		userId: Long,
		projectId: ProjectId
	): StoryExportResult {
		val projectDef = projectEntityDatasource.getProject(userId, projectId)
			?: return StoryExportResult.ProjectNotFound

		return try {
			val sceneDefs = projectEntityDatasource.getEntityDefsByType(
				userId = userId,
				projectDef = projectDef,
				type = ApiProjectEntity.Type.SCENE
			)

			if (sceneDefs.isEmpty()) {
				return StoryExportResult.Success(
					projectName = projectDef.name,
					html = "",
					hasContent = false
				)
			}

			val scenes: List<ApiProjectEntity.SceneEntity> = sceneDefs.mapNotNull { def ->
				val result = projectEntityDatasource.loadEntity(
					userId = userId,
					projectDef = projectDef,
					entityId = def.id,
					entityType = ApiProjectEntity.Type.SCENE,
					serializer = ApiProjectEntity.SceneEntity.serializer()
				)
				if (isSuccess(result)) result.data else null
			}

			val markdown = buildStoryMarkdown(projectDef.name, scenes)

			val html = markdownToHtml(markdown)

			StoryExportResult.Success(
				projectName = projectDef.name,
				html = html,
				hasContent = true
			)
		} catch (e: Exception) {
			StoryExportResult.Error(e.message ?: "Unknown error occurred")
		}
	}

	private fun buildStoryMarkdown(
		projectName: String,
		scenes: List<ApiProjectEntity.SceneEntity>
	): String {
		val builder = StringBuilder()
		builder.append("# $projectName\n\n")

		// Group scenes by parent (last element of path, or ROOT_KEY if empty)
		val scenesByParent: Map<Int, List<ApiProjectEntity.SceneEntity>> = scenes.groupBy { scene ->
			scene.path.lastOrNull() ?: ROOT_KEY
		}

		// Get root-level scenes (direct children of root)
		val rootScenes = scenesByParent[0]?.sortedBy { it.order } ?: emptyList()

		var chapterNumber = 1
		for (scene in rootScenes) {
			builder.append("\n## $chapterNumber. ${scene.name}\n\n")

			if (scene.sceneType == ApiSceneType.Scene) {
				// Write scene content directly
				if (scene.content.isNotBlank()) {
					builder.append(scene.content)
					builder.append("\n")
				}
			} else {
				// It's a Group - write all child scenes' content
				writeGroupChildren(builder, scene.id, scenesByParent)
			}

			chapterNumber++
		}

		return builder.toString()
	}

	private fun writeGroupChildren(
		builder: StringBuilder,
		parentId: Int,
		scenesByParent: Map<Int, List<ApiProjectEntity.SceneEntity>>
	) {
		val children = scenesByParent[parentId]?.sortedBy { it.order } ?: return

		for (child in children) {
			if (child.sceneType == ApiSceneType.Scene) {
				if (child.content.isNotBlank()) {
					builder.append(child.content)
					builder.append("\n")
				}
			} else {
				// Recursively process nested groups
				writeGroupChildren(builder, child.id, scenesByParent)
			}
		}
	}

	private fun markdownToHtml(markdown: String): String {
		val parsedTree = markdownParser.buildMarkdownTreeFromString(markdown)
		return HtmlGenerator(markdown, parsedTree, markdownFlavour).generateHtml()
	}

	companion object {
		private const val ROOT_KEY = -1
	}
}

sealed class StoryExportResult {
	data class Success(
		val projectName: String,
		val html: String,
		val hasContent: Boolean
	) : StoryExportResult()

	data object ProjectNotFound : StoryExportResult()

	data class Error(val message: String) : StoryExportResult()
}
