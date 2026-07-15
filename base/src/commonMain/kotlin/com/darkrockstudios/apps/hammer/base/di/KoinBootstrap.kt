package com.darkrockstudios.apps.hammer.base.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts Koin from the plugin-less `:base`. The Koin compiler plugin runs its full-graph
 * (A3) safety pass wherever [startKoin] is compiled; keeping the call here stops iOS's
 * bootstrap from becoming an A3 aggregator, matching the desktop/android app modules.
 */
fun bootstrapKoin(declaration: KoinAppDeclaration): KoinApplication = startKoin(declaration)
