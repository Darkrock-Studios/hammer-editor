package com.darkrockstudios.apps.hammer.common.components.projectroot

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchComponent
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchState
import com.darkrockstudios.apps.hammer.common.components.globalsearch.SearchResult
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot.ModalDestination.*
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronizationComponent
import com.darkrockstudios.apps.hammer.common.components.protocolmismatch.ProtocolMismatchComponent
import com.darkrockstudios.apps.hammer.common.components.serverreauthentication.ServerReauthenticationComponent
import com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode.FocusModeComponent
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.protocolmismatch.ProtocolMismatchInfo
import kotlinx.serialization.Serializable

class ProjectRootModalRouter(
	componentContext: ComponentContext,
	private val projectDef: ProjectDef,
	private val globalSearchState: GlobalSearchState,
	private val navigateGlobalSearchResult: (SearchResult) -> Unit,
	private val onFocusModeDismissed: (SceneItem) -> Unit,
) : Router {
	private val navigation = SlotNavigation<Config>()

	val state: Value<ChildSlot<Config, ProjectRoot.ModalDestination>> =
		componentContext.childSlot(
			source = navigation,
			initialConfiguration = { Config.None },
			key = "ProjectRootModalRouter",
			childFactory = ::createChild,
			serializer = Config.serializer(),
		)

	override fun isAtRoot(): Boolean {
		return state.value.child?.instance is None
	}

	override fun shouldConfirmClose() = emptySet<CloseConfirm>()

	private fun createChild(
		config: Config,
		componentContext: ComponentContext
	): ProjectRoot.ModalDestination =
		when (config) {
			Config.None -> None
			Config.ProjectSync -> ProjectSync(
				ProjectSynchronizationComponent(
					componentContext,
					projectDef,
					::dismissProjectSync,
					::showReauthorizeDialog
				)
			)

			Config.ServerReauth -> ServerReauth(
				ServerReauthenticationComponent(
					componentContext,
					::dismissProjectSync,
					::showProjectSync,
				)
			)

			is Config.GlobalSearch -> GlobalSearchModal(
				GlobalSearchComponent(
					componentContext,
					projectDef,
					globalSearchState,
					::dismissGlobalSearch,
					navigateGlobalSearchResult,
					initialQuery = config.initialQuery,
				)
			)

			is Config.FocusMode -> FocusModeModal(
				FocusModeComponent(
					componentContext,
					projectDef,
					config.sceneItem,
					closeFocusMode = {
						navigation.activate(Config.None)
						onFocusModeDismissed(config.sceneItem)
					},
				)
			)

			is Config.ProtocolMismatch -> ProtocolMismatchModal(
				ProtocolMismatchComponent(
					componentContext,
					info = ProtocolMismatchInfo(
						clientProtocolVersion = config.clientProtocolVersion,
						serverProtocolVersion = config.serverProtocolVersion,
					),
					dismissDialog = ::dismissProtocolMismatch,
				)
			)
		}

	fun showProjectSync() {
		navigation.activate(Config.ProjectSync)
	}

	fun dismissProjectSync() {
		navigation.activate(Config.None)
	}

	fun showReauthorizeDialog() {
		navigation.activate(Config.ServerReauth)
	}

	fun dismissReauthorizeDialog() {
		navigation.activate(Config.None)
	}

	fun showGlobalSearch(initialQuery: String? = null) {
		navigation.activate(Config.GlobalSearch(initialQuery))
	}

	fun dismissGlobalSearch() {
		navigation.activate(Config.None)
	}

	fun showProtocolMismatch(info: ProtocolMismatchInfo) {
		navigation.activate(
			Config.ProtocolMismatch(
				clientProtocolVersion = info.clientProtocolVersion,
				serverProtocolVersion = info.serverProtocolVersion,
			)
		)
	}

	fun dismissProtocolMismatch() {
		navigation.activate(Config.None)
	}

	fun showFocusMode(sceneItem: SceneItem) {
		navigation.activate(Config.FocusMode(sceneItem))
	}

	fun dismissFocusMode() {
		val active = state.value.child?.configuration as? Config.FocusMode
		navigation.activate(Config.None)
		if (active != null) {
			onFocusModeDismissed(active.sceneItem)
		}
	}

	@Serializable
	sealed class Config {
		@Serializable
		data object None : Config()

		@Serializable
		data object ProjectSync : Config()

		@Serializable
		data object ServerReauth : Config()

		@Serializable
		data class GlobalSearch(val initialQuery: String? = null) : Config()

		@Serializable
		data class FocusMode(val sceneItem: SceneItem) : Config()

		@Serializable
		data class ProtocolMismatch(
			val clientProtocolVersion: Int,
			val serverProtocolVersion: Int?,
		) : Config()
	}
}
