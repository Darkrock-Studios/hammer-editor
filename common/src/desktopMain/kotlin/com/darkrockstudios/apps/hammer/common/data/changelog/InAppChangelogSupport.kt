package com.darkrockstudios.apps.hammer.common.data.changelog

import com.darkrockstudios.apps.hammer.base.DistributionChannel

// Only the Mac App Store flavor faces review; direct downloads keep the dialog.
actual val supportsInAppChangelog: Boolean =
	DistributionChannel.current != DistributionChannel.MAC_APP_STORE
