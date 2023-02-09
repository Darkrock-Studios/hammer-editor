package com.darkrockstudios.apps.hammer.common.util

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleOwner
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

fun CoroutineScope(context: CoroutineContext, lifecycle: Lifecycle): CoroutineScope {
	val scope = CoroutineScope(context)
	lifecycle.doOnDestroy(scope::cancel)
	return scope
}

fun LifecycleOwner.lifecycleCoroutineScope(context: CoroutineContext): CoroutineScope =
	CoroutineScope(context, lifecycle)