package com.darkrockstudios.apps.hammer.common.util

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.ContextCompat.getSystemService


actual class NetworkConnectivity(private val context: Context) {
	actual suspend fun hasActiveConnection(): Boolean {
		val connectivityManager = getSystemService(context, ConnectivityManager::class.java)
		val activeNetworkInfo = connectivityManager?.activeNetworkInfo
		return activeNetworkInfo?.isConnectedOrConnecting == true
	}
}