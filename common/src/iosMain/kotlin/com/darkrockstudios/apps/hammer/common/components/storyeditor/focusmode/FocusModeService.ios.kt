package com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode

actual class FocusModeService {
	// Apple does not expose a public API for apps to toggle Do Not Disturb / Focus
	// (it's user-only by design), so there is nothing for this service to do on iOS.
	actual fun enterFocusMode() {
		// Noop for iOS
	}

	actual fun exitFocusMode() {
		// Noop for iOS
	}
}
