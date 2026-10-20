package com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.projectselection.MoveTarget
import com.darkrockstudios.apps.hammer.common.components.projectroot.Router
import com.darkrockstudios.apps.hammer.common.components.protocolmismatch.ProtocolMismatchComponent
import com.darkrockstudios.apps.hammer.common.components.serverreauthentication.ServerReauthenticationComponent
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.data.protocolmismatch.ProtocolMismatchInfo
import kotlinx.serialization.Serializable

class ProjectListModalRouter(
	componentContext: ComponentContext,
	private val onReauthSuccess: () -> Unit,
) : Router {
	private val navigation = SlotNavigation<Config>()

	val state: Value<ChildSlot<Config, ProjectsList.ModalDestination>> =
		componentContext.childSlot(
			source = navigation,
			initialConfiguration = { Config.None },
			key = "ProjectListModalRouter",
			childFactory = ::createChild,
			serializer = Config.serializer(),
		)

	override fun isAtRoot(): Boolean {
		return state.value.child?.instance is ProjectsList.ModalDestination.None
	}

	override fun shouldConfirmClose() = emptySet<CloseConfirm>()

	private fun createChild(
		config: Config,
		componentContext: ComponentContext
	): ProjectsList.ModalDestination =
		when (config) {
			Config.None -> ProjectsList.ModalDestination.None
			is Config.ProjectSync -> ProjectsList.ModalDestination.ProjectSync
			is Config.ProjectRename -> ProjectsList.ModalDestination.ProjectRename(config.projectDef)
			is Config.ProjectCreate -> ProjectsList.ModalDestination.ProjectCreate(config.parent)
			is Config.ProjectDelete -> ProjectsList.ModalDestination.ProjectDelete(config.projectDef)
			is Config.GroupCreate -> ProjectsList.ModalDestination.GroupCreate(config.parent)
			is Config.GroupRename -> ProjectsList.ModalDestination.GroupRename(config.groupDef)
			is Config.GroupDelete -> ProjectsList.ModalDestination.GroupDelete(config.groupDef)
			is Config.GroupDescription -> ProjectsList.ModalDestination.GroupDescription(config.groupDef)
			is Config.MoveToGroup -> ProjectsList.ModalDestination.MoveToGroup(config.target)
			is Config.ServerReauth -> ProjectsList.ModalDestination.ServerReauth(
				ServerReauthenticationComponent(
					componentContext = componentContext,
					dismissAuth = ::dismissServerReauthentication,
					onReauthSuccess = onReauthSuccess,
				)
			)

			is Config.ProtocolMismatch -> ProjectsList.ModalDestination.ProtocolMismatch(
				ProtocolMismatchComponent(
					componentContext = componentContext,
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

	fun showProjectRename(projectDef: ProjectDef) {
		navigation.activate(Config.ProjectRename(projectDef))
	}

	fun dismissProjectRename() {
		navigation.activate(Config.None)
	}

	fun showProjectCreate(parent: GroupDef? = null) {
		navigation.activate(Config.ProjectCreate(parent))
	}

	fun dismissProjectCreate() {
		navigation.activate(Config.None)
	}

	fun showProjectDelete(projectDef: ProjectDef) {
		navigation.activate(Config.ProjectDelete(projectDef))
	}

	fun dismissProjectDelete() {
		navigation.activate(Config.None)
	}

	fun showGroupCreate(parent: GroupDef?) {
		navigation.activate(Config.GroupCreate(parent))
	}

	fun showGroupRename(groupDef: GroupDef) {
		navigation.activate(Config.GroupRename(groupDef))
	}

	fun showGroupDelete(groupDef: GroupDef) {
		navigation.activate(Config.GroupDelete(groupDef))
	}

	fun showGroupDescription(groupDef: GroupDef) {
		navigation.activate(Config.GroupDescription(groupDef))
	}

	fun showMoveToGroup(target: MoveTarget) {
		navigation.activate(Config.MoveToGroup(target))
	}

	fun dismiss() {
		navigation.activate(Config.None)
	}

	fun showServerReauthentication() {
		navigation.activate(Config.ServerReauth)
	}

	fun dismissServerReauthentication() {
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

	@Serializable
	sealed class Config {
		@Serializable
		data object None : Config()

		@Serializable
		data object ProjectSync : Config()

		@Serializable
		data class ProjectRename(val projectDef: ProjectDef) : Config()

		// parent stays defaulted: a saved config without it must still decode.
		@Serializable
		data class ProjectCreate(val parent: GroupDef? = null) : Config()

		@Serializable
		data class ProjectDelete(val projectDef: ProjectDef) : Config()

		@Serializable
		data class GroupCreate(val parent: GroupDef?) : Config()

		@Serializable
		data class GroupRename(val groupDef: GroupDef) : Config()

		@Serializable
		data class GroupDelete(val groupDef: GroupDef) : Config()

		@Serializable
		data class GroupDescription(val groupDef: GroupDef) : Config()

		@Serializable
		data class MoveToGroup(val target: MoveTarget) : Config()

		@Serializable
		data object ServerReauth : Config()

		@Serializable
		data class ProtocolMismatch(
			val clientProtocolVersion: Int,
			val serverProtocolVersion: Int?,
		) : Config()
	}
}
