package com.darkrockstudios.apps.hammer.common.components

import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.navigate

/** Replaces the top of the stack with [config], dropping any other copy of it further down. */
fun <C : Any> StackNavigation<C>.replaceTop(config: C) {
	navigate { configs -> configs.dropLast(1).filter { it != config } + config }
}
