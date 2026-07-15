package com.darkrockstudios.apps.hammer.base.di

import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext

/**
 * Builds the qualified [CoroutineContext] dispatcher definitions in a module that does
 * NOT carry the Koin compiler plugin.
 *
 * Under `compileSafety = true` the plugin emits a synthetic `dsl_single` hint for every
 * definition in a plugin-processed `module { }`, encoding the `named()` qualifier as a
 * value-parameter NAME. Kotlin/Native's `IdSignature` ignores parameter names, so our
 * three same-type qualified dispatchers (main/default/io — all `CoroutineContext`)
 * collapse to one signature and fail klib serialization. Keeping these three definitions
 * out of the plugin's reach (here, in the plugin-less `:base`) sidesteps the clash while
 * leaving the rest of the graph fully validated. The dispatchers are only ever consumed
 * via runtime `inject(named(...))`, never constructor auto-wiring, so nothing the plugin
 * validates depends on them.
 *
 * Qualifier names are passed in so the `DISPATCHER_*` constants can stay in `:common`
 * alongside their consumers.
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
