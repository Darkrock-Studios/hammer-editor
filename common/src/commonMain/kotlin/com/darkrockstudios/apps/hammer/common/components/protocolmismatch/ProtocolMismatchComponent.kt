package com.darkrockstudios.apps.hammer.common.components.protocolmismatch

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.base.GITHUB_URL
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.protocolmismatch.ProtocolMismatchInfo
import com.darkrockstudios.apps.hammer.common.data.versioncheck.VersionCheckRepository
import com.darkrockstudios.apps.hammer.common.util.UrlLauncher
import com.darkrockstudios.apps.hammer.common.util.getAppVersionString
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class ProtocolMismatchComponent(
	componentContext: ComponentContext,
	info: ProtocolMismatchInfo,
	private val dismissDialog: () -> Unit,
) : ComponentBase(componentContext), ProtocolMismatch {

	private val urlLauncher: UrlLauncher by inject()
	private val versionCheckRepository: VersionCheckRepository by inject()

	private val _state = MutableValue(
		ProtocolMismatch.State(
			clientProtocolVersion = info.clientProtocolVersion,
			serverProtocolVersion = info.serverProtocolVersion,
			clientIsBehind = info.clientIsBehind,
			currentVersion = getAppVersionString(),
		)
	)
	override val state: Value<ProtocolMismatch.State> = _state

	init {
		scope.launch {
			val result = versionCheckRepository.checkForUpdate()
			val release = result.latestRelease
			withContext(dispatcherMain) {
				_state.update {
					it.copy(
						latestVersionTag = release?.bareVersion,
						isNewVersionAvailable = result.isNewVersionAvailable,
						releaseUrl = release?.htmlUrl,
					)
				}
			}
		}
	}

	override fun openReleaseUrl() {
		val url = _state.value.releaseUrl ?: "${GITHUB_URL}releases"
		urlLauncher.openInBrowser(url)
	}

	override fun dismiss() {
		dismissDialog()
	}
}
