package com.darkrockstudios.apps.hammer.android

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.arkivanov.decompose.defaultComponentContext
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRootComponent
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.closeProjectScope
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.data.openProjectScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootFab
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootUi
import com.darkrockstudios.apps.hammer.common.projectroot.getDestinationIcon
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.LocalImageLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.core.component.getScopeId
import org.koin.java.KoinJavaComponent.getKoin

class ProjectRootActivity : AppCompatActivity() {

	private val imageLoader: ImageLoader by inject()
	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val mainDispatcher by injectMainDispatcher()
	private val globalSettings = MutableValue(globalSettingsRepository.globalSettings)
	private var settingsUpdateJob: Job? = null

	private val viewModel: ProjectRootViewModel by viewModels()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		WindowCompat.setDecorFitsSystemWindows(window, false)

		val projectDef = intent.getParcelableExtra<ProjectDef>(EXTRA_PROJECT)
		if (projectDef == null) {
			finish()
		} else {
			viewModel.setProjectDef(projectDef)

			val menu = MutableValue(setOf<MenuDescriptor>())
			val component = ProjectRootComponent(
				componentContext = defaultComponentContext(),
				projectDef = projectDef,
				addMenu = { menuDescriptor ->
					menu.value = mutableSetOf(menuDescriptor).apply { add(menuDescriptor) }
				},
				removeMenu = { menuId ->
					menu.value = menu.value.filter { it.id != menuId }.toSet()
				}
			)

			setContent {
				CompositionLocalProvider(LocalImageLoader provides imageLoader) {
					val settingsState by globalSettings.subscribeAsState()
					val isDark = when (settingsState.uiTheme) {
						UiTheme.Light -> false
						UiTheme.Dark -> true
						UiTheme.FollowSystem -> isSystemInDarkTheme()
					}

					// Dynamic color is available on Android 12+
					val localCtx = LocalContext.current
					fun getDynamicColorScheme(useDark: Boolean): ColorScheme? {
						val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
						return when {
							dynamicColor && useDark -> dynamicDarkColorScheme(localCtx)
							dynamicColor && !useDark -> dynamicLightColorScheme(localCtx)
							else -> null
						}
					}

					AppTheme(isDark, ::getDynamicColorScheme) {
						Content(component, menu)
					}
				}
			}
		}
	}

	override fun onStart() {
		super.onStart()

		settingsUpdateJob = lifecycleScope.launch {
			globalSettingsRepository.globalSettingsUpdates.collect { settings ->
				withContext(mainDispatcher) {
					globalSettings.getAndUpdate { settings }
				}
			}
		}
	}

	override fun onStop() {
		super.onStop()
		settingsUpdateJob?.cancel()
		settingsUpdateJob = null
	}

	@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
	@Composable
	private fun Content(
		component: ProjectRoot,
		menu: MutableValue<Set<MenuDescriptor>>
	) {
		val shouldConfirmClose by component.closeRequestHandlers.subscribeAsState()
		val backEnabled by component.backEnabled.subscribeAsState()

		BackHandler(enabled = backEnabled) {
			component.requestClose()
		}

		val windowSizeClass = calculateWindowSizeClass()
		when (windowSizeClass.widthSizeClass) {
			WindowWidthSizeClass.Compact -> {
				CompactNavigation(component)
			}

			WindowWidthSizeClass.Medium -> {
				MediumNavigation(component)
			}

			WindowWidthSizeClass.Expanded -> {
				ExpandedNavigation(component)
			}
		}

		if (shouldConfirmClose.isNotEmpty()) {
			val item = shouldConfirmClose.first()
			when (item) {
				CloseConfirm.Scenes -> {
					confirmUnsavedScenesDialog(component)
				}

				CloseConfirm.Notes -> {
					confirmCloseUnsavedNotesDialog(component)
				}

				CloseConfirm.Encyclopedia -> {
					confirmCloseUnsavedEncyclopediaDialog(component)
				}

				CloseConfirm.Sync -> {
					component.showProjectSync()
				}

				CloseConfirm.Complete -> {
					finish()
				}
			}
		}
	}

	companion object {
		const val EXTRA_PROJECT = "project"
	}
}

class ProjectRootViewModel : ViewModel() {

	private var projectDef: ProjectDef? = null
	fun setProjectDef(project: ProjectDef) {
		if (projectDef == null) {
			projectDef = project
			runBlocking { openProjectScope(project) }
		}
	}

	override fun onCleared() {
		projectDef?.let {
			closeProjectScope(getKoin().getScope(ProjectDefScope(it).getScopeId()), it)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactNavigation(
	component: ProjectRoot,
) {
	val router by component.routerState.subscribeAsState()
	Scaffold(
		modifier = Modifier
			.fillMaxSize(),
		content = { innerPadding ->
			ProjectRootUi(
				component,
				modifier = Modifier.padding(innerPadding),
			)
		},
		bottomBar = {
			NavigationBar {
				ProjectRoot.DestinationTypes.values().forEach { item ->
					NavigationBarItem(
						selected = item == router.active.instance.getLocationType(),
						onClick = { component.showDestination(item) },
						icon = { Icon(imageVector = getDestinationIcon(item), contentDescription = item.text.get()) },
					)
				}
			}
		},
		floatingActionButton = {
			ProjectRootFab(component)
		}
	)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
private fun MediumNavigation(
	component: ProjectRoot,
) {
	val router by component.routerState.subscribeAsState()
	Scaffold(
		modifier = Modifier
			.fillMaxSize(),
		content = { innerPadding ->
			Row(modifier = Modifier.fillMaxSize()) {
				NavigationRail(
					modifier = Modifier
						.padding(top = Ui.Padding.M)
						.background(Color.Blue)
				) {
					ProjectRoot.DestinationTypes.values().forEach { item ->
						NavigationRailItem(
							label = { Text(item.text.get()) },
							icon = {
								Icon(
									imageVector = getDestinationIcon(item),
									contentDescription = item.text.get()
								)
							},
							selected = item == router.active.instance.getLocationType(),
							onClick = { component.showDestination(item) },
						)
					}

					Spacer(modifier = Modifier.weight(1f))

					Text(
						"v${BuildMetadata.APP_VERSION}",
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.Thin,
						modifier = Modifier
							.align(Alignment.Start)
							.padding(Ui.Padding.L)
					)
				}

				ProjectRootUi(component, Modifier.padding(innerPadding))
			}
		},
		floatingActionButton = {
			ProjectRootFab(component)
		}
	)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
private fun ExpandedNavigation(
	component: ProjectRoot,
) {
	val router by component.routerState.subscribeAsState()
	Scaffold(
		modifier = Modifier.fillMaxSize(),
		content = { innerPadding ->
			PermanentNavigationDrawer(
				modifier = Modifier.fillMaxSize(),
				drawerContent = {
					PermanentDrawerSheet(modifier = Modifier.width(Ui.NavDrawer.widthExpanded)) {
						Spacer(Modifier.height(12.dp))
						ProjectRoot.DestinationTypes.values().forEach { item ->
							NavigationDrawerItem(
								label = { Text(item.text.get()) },
								icon = {
									Icon(
										imageVector = getDestinationIcon(item),
										contentDescription = item.text.get()
									)
								},
								selected = item == router.active.instance.getLocationType(),
								onClick = { component.showDestination(item) },
								modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
							)
						}

						Spacer(modifier = Modifier.weight(1f))

						Text(
							"v${BuildMetadata.APP_VERSION}",
							modifier = Modifier
								.padding(Ui.Padding.L)
								.align(Alignment.Start),
							style = MaterialTheme.typography.labelSmall,
						)
					}
				},
				content = {
					ProjectRootUi(component, Modifier.padding(innerPadding))
				}
			)
		},
		floatingActionButton = {
			ProjectRootFab(component)
		}
	)
}