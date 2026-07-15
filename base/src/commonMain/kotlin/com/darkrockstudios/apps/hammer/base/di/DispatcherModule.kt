package com.darkrockstudios.apps.hammer.base.di

import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext

/**
 * The three same-type qualified [CoroutineContext] dispatchers live here, in the
 * plugin-less `:base`, rather than in a Koin-compiler-plugin module. Under
 * `compileSafety = true` the plugin encodes each `named()` qualifier as a parameter NAME,
 * which Kotlin/Native's `IdSignature` ignores — so same-type qualified definitions clash
 * during klib serialization. Qualifier names are passed in so the `DISPATCHER_*` constants
 * can stay in `:common` with their consumers.
 */
fun dispatcherModule(
	mainQualifier: String,
	defaultQualifier: String,
	ioQualifier: String,
	mainDispatcher: CoroutineContext,
	defaultDispatcher: CoroutineContext,
	ioDispatcher: CoroutineContext,
): Module = module {
	single(qualifier = named(mainQualifier)) { mainDispatcher }
	single(qualifier = named(defaultQualifier)) { defaultDispatcher }
	single(qualifier = named(ioQualifier)) { ioDispatcher }
}
