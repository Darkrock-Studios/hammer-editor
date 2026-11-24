package com.darkrockstudios.apps.hammer.common.data.nux

/** A one-time coach mark. [id] is persisted, so never change one once shipped. It only shows on [platforms]. */
enum class NuxHint(val id: String, val platforms: Set<NuxPlatform> = NuxPlatform.entries.toSet()) {
	/** Describes the swipe in the Android `SceneItemActionContainer` actual. */
	SceneListSwipeMenu("scene_list.swipe_menu", setOf(NuxPlatform.Android)),

	/** Describes the context menu in the desktop `SceneItemActionContainer` actual. */
	SceneListContextMenu("scene_list.context_menu", setOf(NuxPlatform.Desktop)),
	SceneListReorder("scene_list.reorder"),
}
