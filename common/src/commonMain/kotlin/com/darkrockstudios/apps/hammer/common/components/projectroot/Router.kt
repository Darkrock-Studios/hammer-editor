package com.darkrockstudios.apps.hammer.common.components.projectroot

interface Router {
	fun isAtRoot(): Boolean
	fun shouldConfirmClose(): Set<CloseConfirm>
}