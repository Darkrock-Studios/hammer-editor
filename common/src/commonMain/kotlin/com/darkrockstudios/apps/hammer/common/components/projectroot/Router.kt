package com.darkrockstudios.apps.hammer.common.components.projectroot

interface Router {
	fun isAtRoot(): Boolean
	fun shouldConfirmClose(): Set<CloseConfirm>

	/** Throws away the in-progress edit that made [shouldConfirmClose] report [item]. */
	fun discardUnsaved(item: CloseConfirm) {}
}