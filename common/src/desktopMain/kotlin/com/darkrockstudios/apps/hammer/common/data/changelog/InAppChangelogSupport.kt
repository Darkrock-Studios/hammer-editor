package com.darkrockstudios.apps.hammer.common.data.changelog

import com.darkrockstudios.apps.hammer.common.IS_APP_STORE

// Only the Mac App Store flavor faces review; direct downloads keep the dialog.
actual val supportsInAppChangelog: Boolean = !IS_APP_STORE
