package com.darkrockstudios.apps.hammer.wear.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

// Defined as a string literal so the module still compiles against SDKs below 37, where the
// Manifest.permission constant is absent.
const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val LOCAL_NETWORK_MIN_SDK = 37

/**
 * Android 17 blocks an app from reaching local network addresses until it holds
 * [ACCESS_LOCAL_NETWORK]. A blocked connection does not fail fast; it times out as a generic
 * connection error, so the permission has to be settled before connecting rather than diagnosed
 * after.
 */
interface LocalNetworkAccess {
	/** True when [serverAddress] is on the local network and the app may not reach it yet. */
	suspend fun isBlocked(serverAddress: String): Boolean
}

class AndroidLocalNetworkAccess(
	private val context: Context,
	private val ioDispatcher: CoroutineContext,
) : LocalNetworkAccess {

	override suspend fun isBlocked(serverAddress: String): Boolean {
		if (Build.VERSION.SDK_INT < LOCAL_NETWORK_MIN_SDK) return false
		if (ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) {
			return false
		}
		// Resolving a name is a network call.
		return withContext(ioDispatcher) { isLocalNetworkServer(serverAddress) }
	}
}
