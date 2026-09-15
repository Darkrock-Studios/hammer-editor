package com.darkrockstudios.apps.hammer.android.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import com.darkrockstudios.apps.hammer.android.R
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.data.pairing.PairRequest
import com.darkrockstudios.apps.hammer.common.data.pairing.PairResponse
import com.darkrockstudios.apps.hammer.common.data.pairing.PairingProtocol
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class WearPairingActivity : ComponentActivity(), KoinComponent {

	private val handler: WearPairingHandler by inject()
	private val globalSettingsStore: GlobalSettingsStore by inject()
	private val appScope: CoroutineScope by inject(named(APP_SCOPE))

	private var nodeId: String? = null
	private var request: PairRequest? = null
	private var answered = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		// Drop touches delivered while another app's window overlays this dialog, so a tapjacking
		// overlay can't approve a pairing on the user's behalf.
		window.decorView.filterTouchesWhenObscured = true
		setFinishOnTouchOutside(false)
		window.setBackgroundDrawableResource(android.R.color.transparent)

		val sourceNode = intent.getStringExtra(EXTRA_NODE_ID)
		val pairRequest = intent.getByteArrayExtra(EXTRA_REQUEST)?.let(PairingProtocol::decodeRequest)
		if (sourceNode == null || pairRequest == null) {
			finish()
			return
		}
		nodeId = sourceNode
		request = pairRequest
		NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)

		val accountEmail = if (handler.isSignedIn()) globalSettingsStore.serverSettings?.email else null
		val settings = globalSettingsStore.globalSettings

		setContent {
			val isDark = when (settings.uiTheme) {
				UiTheme.Light -> false
				UiTheme.Dark -> true
				UiTheme.FollowSystem -> isSystemInDarkTheme()
			}

			BackHandler { decline() }

			AppTheme(settings = settings, useDarkTheme = isDark) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.systemBarsPadding()
						.padding(Ui.Padding.M),
					contentAlignment = Alignment.Center,
				) {
					WearPairingDialog(
						deviceLabel = pairRequest.deviceLabel,
						accountEmail = accountEmail,
						onApprove = ::approve,
						onDecline = ::decline,
					)
				}
			}
		}
	}

	// The reply runs in the app scope so it still reaches the watch after this dialog closes.
	private fun approve() {
		val sourceNode = nodeId ?: return
		val pairRequest = request ?: return
		answered = true
		val appContext = applicationContext
		appScope.launch {
			val response = handler.approve(sourceNode, pairRequest)
			val message = when (response) {
				is PairResponse.Success -> R.string.wear_pairing_toast_success
				is PairResponse.Error -> R.string.wear_pairing_toast_failure
			}
			if (handler.isSignedIn()) {
				withContext(Dispatchers.Main) {
					Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
				}
			}
		}
		finish()
	}

	private fun decline() {
		val sourceNode = nodeId ?: return
		val pairRequest = request ?: return
		answered = true
		appScope.launch { handler.decline(sourceNode, pairRequest) }
		finish()
	}

	override fun onDestroy() {
		super.onDestroy()
		// Dismissed without an answer: tell the watch rather than leave it waiting.
		if (!answered && !isChangingConfigurations) {
			decline()
		}
	}

	companion object {
		const val NOTIFICATION_ID = 7301
		private const val EXTRA_NODE_ID = "node_id"
		private const val EXTRA_REQUEST = "request"

		fun createIntent(context: Context, nodeId: String, requestPayload: ByteArray): Intent =
			Intent(context, WearPairingActivity::class.java)
				.putExtra(EXTRA_NODE_ID, nodeId)
				.putExtra(EXTRA_REQUEST, requestPayload)
	}
}
