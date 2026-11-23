package com.darkrockstudios.apps.hammer.common.data.nux

/** A one-time coach mark. [id] is persisted, so never change one once shipped. */
enum class NuxHint(val id: String) {
	SceneListSwipeMenu("scene_list.swipe_menu"),
	SceneListReorder("scene_list.reorder"),
}
