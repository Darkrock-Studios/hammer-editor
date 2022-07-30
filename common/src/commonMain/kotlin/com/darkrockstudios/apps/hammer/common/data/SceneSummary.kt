package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.tree.ImmutableTree

data class SceneSummary(
    val sceneTree: ImmutableTree<SceneItem>,
    val hasDirtyBuffer: Set<Int>
)