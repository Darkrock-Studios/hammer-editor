package com.darkrockstudios.apps.hammer.common.components.projectroot

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.Encyclopedia
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.EncyclopediaComponent
import com.darkrockstudios.apps.hammer.common.components.notes.Notes
import com.darkrockstudios.apps.hammer.common.components.notes.NotesComponent
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHomeComponent
import com.darkrockstudios.apps.hammer.common.components.storyeditor.StoryEditor
import com.darkrockstudios.apps.hammer.common.components.storyeditor.StoryEditorComponent
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLine
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineComponent
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext

internal class ProjectRootRouter(
	componentContext: ComponentContext,
	private val projectDef: ProjectDef,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
	private val updateShouldClose: () -> Unit,
	private val showProjectSync: () -> Unit,
	private val scope: CoroutineScope,
	private val dispatcherMain: CoroutineContext,
) : Router {

	private val navigation = StackNavigation<Config>()

	val state: Value<ChildStack<Config, ProjectRoot.Destination<*>>> =
		componentContext.childStack(
			source = navigation,
			initialConfiguration = Config.HomeConfig(projectDef),
			key = "ProjectRootRouter",
			childFactory = ::createChild,
			serializer = Config.serializer()
		)

	private fun createChild(
		config: Config,
		componentContext: ComponentContext
	): ProjectRoot.Destination<*> =
		when (config) {
			is Config.EditorConfig -> ProjectRoot.Destination.EditorDestination(
				editorComponent(config, componentContext)
			)

			is Config.NotesConfig -> ProjectRoot.Destination.NotesDestination(
				notes(config, componentContext)
			)

			is Config.EncyclopediaConfig -> ProjectRoot.Destination.EncyclopediaDestination(
				encyclopedia(config, componentContext)
			)

			is Config.TimeLineConfig -> ProjectRoot.Destination.TimeLineDestination(
				timeLine(config, componentContext)
			)

			is Config.HomeConfig -> ProjectRoot.Destination.HomeDestination(
				home(config, componentContext)
			)
		}

	private fun editorComponent(config: Config.EditorConfig, componentContext: ComponentContext): StoryEditor {
		val editor = StoryEditorComponent(
			componentContext = componentContext,
			projectDef = config.projectDef,
			addMenu = addMenu,
			removeMenu = removeMenu
		)

		scope.launch {
			editor.shouldCloseRoot.collect {
				withContext(dispatcherMain) {
					updateShouldClose()
				}
			}
		}

		return editor
	}

	private fun notes(config: Config.NotesConfig, componentContext: ComponentContext): Notes {
		Napier.d("create notes")
		return NotesComponent(
			componentContext = componentContext,
			projectDef = config.projectDef,
			updateShouldClose = updateShouldClose,
			addMenu = addMenu,
			removeMenu = removeMenu
		)
	}

	private fun encyclopedia(config: Config.EncyclopediaConfig, componentContext: ComponentContext): Encyclopedia {
		return EncyclopediaComponent(
			componentContext = componentContext,
			projectDef = config.projectDef,
			updateShouldClose = updateShouldClose,
			addMenu = addMenu,
			removeMenu = removeMenu
		)
	}

	private fun timeLine(config: Config.TimeLineConfig, componentContext: ComponentContext): TimeLine {
		return TimeLineComponent(
			componentContext = componentContext,
			projectDef = config.projectDef,
			updateShouldClose = updateShouldClose,
			addMenu = addMenu,
			removeMenu = removeMenu
		)
	}

	private fun home(config: Config.HomeConfig, componentContext: ComponentContext): ProjectHome {
		return ProjectHomeComponent(
			componentContext = componentContext,
			projectDef = config.projectDef,
			showProjectSync = showProjectSync
		)
	}


	fun showEditor() {
		navigation.bringToFront(Config.EditorConfig(projectDef = projectDef))
	}

	fun showNotes() {
		navigation.bringToFront(Config.NotesConfig(projectDef = projectDef))
		Napier.d("notes brought to front")
	}

	fun showEncyclopedia() {
		navigation.bringToFront(Config.EncyclopediaConfig(projectDef = projectDef))
	}

	fun showTimeLine() {
		navigation.bringToFront(Config.TimeLineConfig(projectDef = projectDef))
	}

	fun showHome() {
		navigation.bringToFront(Config.HomeConfig(projectDef = projectDef))
	}

	override fun isAtRoot(): Boolean {
		val router = state.value.active.instance as? Router
		return router?.isAtRoot() ?: true
	}

	override fun shouldConfirmClose(): Set<CloseConfirm> {
		return state.value.items.flatMap {
			it.instance.shouldConfirmClose()
		}.toSet()
	}

	init {
		navigation.subscribe { updateShouldClose() }
	}

	@Serializable
	sealed class Config {
		@Serializable
		data class EditorConfig(val projectDef: ProjectDef) : Config()

		@Serializable
		data class NotesConfig(val projectDef: ProjectDef) : Config()

		@Serializable
		data class EncyclopediaConfig(val projectDef: ProjectDef) : Config()

		@Serializable
		data class TimeLineConfig(val projectDef: ProjectDef) : Config()

		@Serializable
		data class HomeConfig(val projectDef: ProjectDef) : Config()
	}
}