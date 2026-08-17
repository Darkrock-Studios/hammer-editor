package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import kotlinx.serialization.Serializable

/** One row of the flattened scene tree shown in the export dialog. */
@Serializable
data class ExportableScene(
	val id: Int,
	val name: String,
	val isGroup: Boolean,
	/** 0 for top-level nodes. */
	val depth: Int,
)

/** Flattens [tree] in render order (preorder depth-first), excluding the root. */
fun exportableScenes(tree: ImmutableTree<SceneItem>): List<ExportableScene> =
	tree.root
		.filter { it.value.type != SceneItem.Type.Root }
		.map { node ->
			ExportableScene(
				id = node.value.id,
				name = node.value.name,
				isGroup = node.value.type.isCollection,
				depth = node.depth - 1,
			)
		}
