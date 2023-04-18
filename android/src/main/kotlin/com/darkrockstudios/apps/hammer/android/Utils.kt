package com.darkrockstudios.apps.hammer.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun isInternetConnected(context: Context): Boolean {
	val connectivityManager =
		context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

	connectivityManager.activeNetwork?.let { network ->
		val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
		return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
			?: false
	}
	return false
}