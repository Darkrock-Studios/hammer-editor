package com.darkrockstudios.apps.hammer.common.components.storyeditor.drafts

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.diff.DiffResult
import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface DraftCompare : HammerComponent {
	val sceneItem: SceneItem
	val draftDef: DraftDef

	val state: Value<State>

	fun loadContents()
	fun onMergedContentChanged(richText: PlatformRichText)
	fun onCurrentMarkdownChanged(markdown: String)
	fun setShowDiff(show: Boolean)
	fun pickDraft()
	fun pickMerged()

	fun cancel()

	data class State(
		val sceneItem: SceneItem,
		val draftDef: DraftDef,
		val sceneContent: SceneContent? = null,
		val mergedContent: PlatformRichText? = null,
		val draftContent: SceneContent? = null,
		val diffResult: DiffResult? = null,
		val showDiff: Boolean = true,
	)
}