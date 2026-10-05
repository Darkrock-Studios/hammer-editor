package data.changelog

import com.darkrockstudios.apps.hammer.base.DistributionChannel
import com.darkrockstudios.apps.hammer.common.data.changelog.supportsInAppChangelog
import kotlin.test.Test
import kotlin.test.assertEquals

class InAppChangelogSupportTest {

	@Test
	fun `only the Mac App Store build hides the in-app changelog`() {
		for (channel in DistributionChannel.entries) {
			val expected = channel != DistributionChannel.MAC_APP_STORE
			assertEquals(expected, supportsInAppChangelog(channel), "channel ${channel.token}")
		}
	}
}
