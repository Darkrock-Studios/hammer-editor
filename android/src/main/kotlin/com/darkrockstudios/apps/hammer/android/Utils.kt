package com.darkrockstudios.apps.hammer.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
fun Modifier.rootElement(scaffoldPadding: PaddingValues): Modifier {
	return this.then(
		fillMaxSize()
			.padding(scaffoldPadding)
			.consumeWindowInsets(scaffoldPadding)
	)
}

fun Modifier.defaultScaffold(): Modifier {
	return this.then(
		imePadding()
			.systemBarsPadding()
			.fillMaxSize()
	)
}