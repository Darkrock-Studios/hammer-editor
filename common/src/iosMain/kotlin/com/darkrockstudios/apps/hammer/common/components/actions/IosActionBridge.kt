package com.darkrockstudios.apps.hammer.common.components.actions

// For Swift, whose key commands carry the action id as a plain string in UIKeyCommand.propertyList.

fun ActionDispatcher.invokeRaw(actionId: String): Boolean = invoke(ActionId(actionId))

fun ActionDispatcher.isLiveRaw(actionId: String): Boolean = isLive(ActionId(actionId))
