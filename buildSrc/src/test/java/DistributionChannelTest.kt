import com.darkrockstudios.build.DistributionChannel
import com.darkrockstudios.build.resolveDistributionChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DistributionChannelTest {

	@Test
	fun `no channel property is a dev build`() {
		assertEquals(DistributionChannel.DEV, resolveDistributionChannel(null, isFDroid = false))
		assertEquals(DistributionChannel.DEV, resolveDistributionChannel("", isFDroid = false))
		assertEquals(DistributionChannel.DEV, resolveDistributionChannel("  ", isFDroid = false))
	}

	@Test
	fun `the fdroid build flag stands in for the channel property`() {
		assertEquals(DistributionChannel.FDROID, resolveDistributionChannel(null, isFDroid = true))
	}

	@Test
	fun `an explicit channel wins over the fdroid flag`() {
		assertEquals(
			DistributionChannel.GITHUB,
			resolveDistributionChannel("github", isFDroid = true)
		)
	}

	@Test
	fun `every token resolves to its own channel`() {
		for (channel in DistributionChannel.entries) {
			assertEquals(channel, resolveDistributionChannel(channel.token, isFDroid = false))
		}
	}

	/** A typo in a release workflow must fail the build, not ship a store binary as DEV. */
	@Test
	fun `an unknown token fails the build`() {
		val error = assertFailsWith<IllegalStateException> {
			resolveDistributionChannel("microsoftStore", isFDroid = false)
		}
		assertEquals(true, error.message?.contains("ms-store"))
	}

	@Test
	fun `tokens are unique`() {
		val tokens = DistributionChannel.entries.map { it.token }
		assertEquals(tokens.size, tokens.toSet().size)
	}
}
