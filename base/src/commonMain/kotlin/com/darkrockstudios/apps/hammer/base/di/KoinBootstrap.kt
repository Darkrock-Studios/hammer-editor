package com.darkrockstudios.apps.hammer.base.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts Koin from a module that does not carry the Koin compiler plugin.
 *
 * The plugin runs its full-graph (A3) safety pass wherever a [startKoin] call is
 * compiled. Keeping that call here — outside the plugin — means a platform bootstrap
 * (notably iOS, whose entry point lives in the plugin-processed common module) can
 * start Koin without turning its own compilation into an A3 aggregator. This matches
 * how the desktop and android app modules already sit outside the plugin.
 */
fun bootstrapKoin(declaration: KoinAppDeclaration): KoinApplication = startKoin(declaration)
