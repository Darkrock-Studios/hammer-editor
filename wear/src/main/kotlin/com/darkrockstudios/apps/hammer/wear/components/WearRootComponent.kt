package com.darkrockstudios.apps.hammer.wear.components

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.wear.components.pairing.PairingComponent
import com.darkrockstudios.apps.hammer.wear.components.projects.WearProjectsComponent
import com.darkrockstudios.apps.hammer.wear.components.signin.ManualSignInComponent
import com.darkrockstudios.apps.hammer.wear.data.isSignedIn
import com.darkrockstudios.apps.hammer.wear.pairing.PhonePairingUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WearRootComponent(
	componentContext: ComponentContext,
	private val globalSettingsStore: GlobalSettingsStore,
	private val phonePairing: PhonePairingUseCase,
	private val accountUseCase: AccountUseCase,
	private val strRes: StrRes,
	private val deviceLabel: String,
) : ComponentBase(componentContext), WearRoot {

	private val navigation = StackNavigation<WearRoot.Config>()

	override val stack: Value<ChildStack<WearRoot.Config, WearRoot.Destination>> = childStack(
		source = navigation,
		serializer = WearRoot.Config.serializer(),
		initialConfiguration = if (globalSettingsStore.serverSettings.isSignedIn()) {
			WearRoot.Config.Projects
		} else {
			WearRoot.Config.Onboarding
		},
		handleBackButton = true,
		childFactory = ::createChild,
	)

	override fun onCreate() {
		super.onCreate()
		scope.launch {
			globalSettingsStore.serverSettingsUpdates.collect { settings ->
				withContext(dispatcherMain) {
					val signedIn = settings.isSignedIn()
					val onProjects = stack.value.active.configuration == WearRoot.Config.Projects
					if (signedIn && !onProjects) {
						navigation.replaceAll(WearRoot.Config.Projects)
					} else if (!signedIn && onProjects) {
						navigation.replaceAll(WearRoot.Config.Onboarding)
					}
				}
			}
		}
	}

	override fun showPairing() {
		navigation.pushNew(WearRoot.Config.Pairing)
	}

	override fun showManualSignIn() {
		navigation.pushNew(WearRoot.Config.ManualSignIn)
	}

	override fun onBack() {
		if (stack.value.backStack.isNotEmpty()) {
			navigation.pop()
		}
	}

	private fun createChild(
		config: WearRoot.Config,
		componentContext: ComponentContext,
	): WearRoot.Destination = when (config) {
		WearRoot.Config.Onboarding -> WearRoot.Destination.Onboarding

		WearRoot.Config.Pairing -> WearRoot.Destination.PairingDestination(
			PairingComponent(
				componentContext = componentContext,
				phonePairing = phonePairing,
				deviceLabel = deviceLabel,
				onCancel = ::onBack,
			)
		)

		WearRoot.Config.ManualSignIn -> WearRoot.Destination.ManualSignInDestination(
			ManualSignInComponent(
				componentContext = componentContext,
				accountUseCase = accountUseCase,
				strRes = strRes,
			)
		)

		WearRoot.Config.Projects -> WearRoot.Destination.ProjectsDestination(
			WearProjectsComponent(
				componentContext = componentContext,
				globalSettingsStore = globalSettingsStore,
			)
		)
	}
}
