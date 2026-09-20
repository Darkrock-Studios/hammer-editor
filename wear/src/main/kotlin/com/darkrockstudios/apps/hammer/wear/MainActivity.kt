package com.darkrockstudios.apps.hammer.wear

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arkivanov.decompose.retainedComponent
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.wear.components.WearRootComponent
import com.darkrockstudios.apps.hammer.wear.ui.WearRootUi
import com.darkrockstudios.apps.hammer.wear.ui.theme.HammerWearTheme
import org.koin.android.ext.android.get
import org.koin.core.qualifier.named

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val root = retainedComponent { componentContext ->
			WearRootComponent(
				componentContext = componentContext,
				globalSettingsStore = get(),
				phonePairing = get(),
				accountUseCase = get(),
				listProjects = get(),
				projectsRepository = get(),
				subscriptions = get(),
				unsyncedContent = get(),
				syncCoordinator = get(),
				signOutUseCase = get(),
				localNetworkAccess = get(),
				appScope = get(named(APP_SCOPE)),
				strRes = get(),
				deviceLabel = Build.MODEL,
			)
		}

		setContent {
			HammerWearTheme {
				WearRootUi(root)
			}
		}
	}
}
