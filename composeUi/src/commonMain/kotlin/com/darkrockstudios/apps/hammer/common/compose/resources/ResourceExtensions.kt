package com.darkrockstudios.apps.hammer.common.compose.resources

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun StringResource.get(): String {
	val resolved = stringResource(this)
	recordKey(this, resolved)
	return resolved
}

@Composable
fun StringResource.get(vararg args: Any): String {
	val resolved = stringResource(this, *args)
	recordKey(this, resolved)
	return resolved
}

@Composable
private fun recordKey(resource: StringResource, resolved: String) {
	val recorder = LocalStringKeyRecorder.current ?: return
	recorder.record(resource.key, resolved)
}
