package com.darkrockstudios.apps.hammer.common.projectselection.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

// Defined as a string literal so the module still compiles against SDKs below 37, where the
// Manifest.permission constant is absent.
private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
actual fun RequestLocalNetworkPermission(show: Boolean) {
	if (Build.VERSION.SDK_INT < 37) return

	val context = LocalContext.current
	val launcher = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission(),
	) { /* If denied, a later connection to a LAN server fails through the normal error path. */ }

	LaunchedEffect(show) {
		val granted = ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) ==
			PackageManager.PERMISSION_GRANTED
		if (show && !granted) {
			launcher.launch(ACCESS_LOCAL_NETWORK)
		}
	}
}
