package com.darkrockstudios.apps.hammer.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier

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

@OptIn(ExperimentalLayoutApi::class)
fun Modifier.rootElement(
	scaffoldPadding: PaddingValues,
): Modifier {
	return this.then(
		Modifier
			.fillMaxSize()
			.padding(scaffoldPadding)
			.consumeWindowInsets(scaffoldPadding)
			.systemBarsPadding()
	)
}

fun Modifier.fab(): Modifier {
	return this.then(
		Modifier
			.systemBarsPadding()
			.navigationBarsPadding()
	)
}

fun Modifier.defaultScaffold(): Modifier {
	return this.then(
		imePadding().fillMaxSize()
	)
}