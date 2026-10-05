package com.darkrockstudios.apps.hammer.common.data.changelog

import com.darkrockstudios.apps.hammer.base.DistributionChannel

actual val supportsInAppChangelog: Boolean = supportsInAppChangelog(DistributionChannel.current)

// Only the Mac App Store flavor faces review; direct downloads keep the dialog.
internal fun supportsInAppChangelog(channel: DistributionChannel): Boolean =
	channel != DistributionChannel.MAC_APP_STORE
