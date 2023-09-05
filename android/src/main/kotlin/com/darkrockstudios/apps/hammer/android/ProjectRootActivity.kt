package com.darkrockstudios.apps.hammer.android

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.retainedComponent
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRootComponent
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
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
import com.darkrockstudios.apps.hammer.common.util.getAppVersionString
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

	@OptIn(ExperimentalDecomposeApi::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		WindowCompat.setDecorFitsSystemWindows(window, false)

		val projectDef = intent.getParcelableExtra<ProjectDef>(EXTRA_PROJECT)
		if (projectDef == null) {
			finish()
		} else {
			viewModel.setProjectDef(projectDef)

			val component = retainedComponent { componentContext ->
				ProjectRootComponent(
					componentContext = componentContext,
					projectDef = projectDef,
					addMenu = { /* Not needed on Android */ },
					removeMenu = { /* Not needed on Android */ }
				)
			}

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
						Content(component)
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
			when (shouldConfirmClose.first()) {
				CloseConfirm.Scenes -> {
					ConfirmUnsavedScenesDialog(component, lifecycleScope)
				}

				CloseConfirm.Notes -> {
					ConfirmCloseUnsavedNotesDialog(component)
				}

				CloseConfirm.Encyclopedia -> {
					ConfirmCloseUnsavedEncyclopediaDialog(component)
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

@Composable
private fun CompactNavigation(
	component: ProjectRoot,
) {
	val router by component.routerState.subscribeAsState()
	Scaffold(
		modifier = Modifier.defaultScaffold(),
		contentWindowInsets = WindowInsets(0, 0, 0, 0),
		content = { scaffoldPadding ->
			Box(modifier = Modifier.fillMaxSize())
			ProjectRootUi(
				component,
				modifier = Modifier.rootElement(scaffoldPadding),
			)
		},
		bottomBar = {
			NavigationBar {
				ProjectRoot.DestinationTypes.values().forEach { item ->
					NavigationBarItem(
						selected = item == router.active.instance.getLocationType(),
						onClick = { component.showDestination(item) },
						icon = {
							Icon(
								imageVector = getDestinationIcon(item),
								contentDescription = item.text.get()
							)
						},
					)
				}
			}
		},
		floatingActionButton = {
			ProjectRootFab(component, Modifier.fab())
		}
	)
}

@Composable
private fun MediumNavigation(
	component: ProjectRoot,
) {
	val router by component.routerState.subscribeAsState()
	Scaffold(
		modifier = Modifier.defaultScaffold(),
		contentWindowInsets = WindowInsets(0, 0, 0, 0),
		content = { scaffoldPadding ->
			Row(
				modifier = Modifier.rootElement(scaffoldPadding)
			) {
				NavigationRail(
					modifier = Modifier
						.padding(top = Ui.Padding.M)
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
						getAppVersionString(),
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.Thin,
						modifier = Modifier
							.align(Alignment.Start)
							.padding(Ui.Padding.L)
					)
				}

				ProjectRootUi(component, Modifier.padding(scaffoldPadding))
			}
		},
		floatingActionButton = {
			ProjectRootFab(component, Modifier.fab())
		}
	)
}

@Composable
private fun ExpandedNavigation(
	component: ProjectRoot,
) {
	val router by component.routerState.subscribeAsState()
	Scaffold(
		modifier = Modifier.defaultScaffold(),
		contentWindowInsets = WindowInsets(0, 0, 0, 0),
		content = { scaffoldPadding ->
			PermanentNavigationDrawer(
				modifier = Modifier.rootElement(scaffoldPadding),
				drawerContent = {
					PermanentDrawerSheet(
						modifier = Modifier
							.wrapContentWidth()
							.width(IntrinsicSize.Min)
					) {
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
							getAppVersionString(),
							modifier = Modifier
								.padding(Ui.Padding.L)
								.align(Alignment.Start),
							style = MaterialTheme.typography.labelSmall,
						)
					}
				},
				content = {
					ProjectRootUi(component, Modifier.rootElement(scaffoldPadding))
				}
			)
		},
		floatingActionButton = {
			ProjectRootFab(component, Modifier.fab())
		}
	)
}