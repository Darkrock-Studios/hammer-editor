package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.utilities.coerceToStorableRange
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.test.assertEquals

class InstantColumnAdapterTest {

	@Test
	fun `coerceToStorableRange leaves in-range values untouched`() {
		val instant = Instant.fromEpochSeconds(1_700_000_000)
		assertEquals(instant, instant.coerceToStorableRange())
		assertEquals(Instant.DISTANT_PAST, Instant.DISTANT_PAST.coerceToStorableRange())
		assertEquals(Instant.DISTANT_FUTURE, Instant.DISTANT_FUTURE.coerceToStorableRange())
	}

	@Test
	fun `coerceToStorableRange clamps out-of-range values to the sentinels`() {
		assertEquals(
			Instant.DISTANT_PAST,
			Instant.fromEpochSeconds(-31_557_014_167_219_200).coerceToStorableRange(),
		)
		assertEquals(
			Instant.DISTANT_FUTURE,
			Instant.fromEpochSeconds(31_556_889_864_403_199).coerceToStorableRange(),
		)
	}

	@Test
	fun `round trips a normal instant`() {
		val instant = Instant.fromEpochSeconds(1_700_000_000)
		val encoded = InstantColumnAdapter.encode(instant)
		assertEquals(instant, InstantColumnAdapter.decode(encoded))
	}

	@Test
	fun `encodes DISTANT_PAST without throwing`() {
		val encoded = InstantColumnAdapter.encode(Instant.DISTANT_PAST)
		assertEquals(Instant.DISTANT_PAST, InstantColumnAdapter.decode(encoded))
	}

	@Test
	fun `encodes DISTANT_FUTURE without throwing`() {
		val encoded = InstantColumnAdapter.encode(Instant.DISTANT_FUTURE)
		assertEquals(Instant.DISTANT_FUTURE, InstantColumnAdapter.decode(encoded))
	}

	@Test
	fun `clamps an instant below the representable range instead of throwing`() {
		// java.time.Instant.MIN (year -1000000000): a valid instant whose UTC LocalDate
		// is one day below LocalDate.MIN, which is what produced the production 500.
		val belowRange = Instant.fromEpochSeconds(-31_557_014_167_219_200)
		val encoded = InstantColumnAdapter.encode(belowRange)
		assertEquals(Instant.DISTANT_PAST, InstantColumnAdapter.decode(encoded))
	}

	@Test
	fun `clamps an instant above the representable range instead of throwing`() {
		val aboveRange = Instant.fromEpochSeconds(31_556_889_864_403_199)
		val encoded = InstantColumnAdapter.encode(aboveRange)
		assertEquals(Instant.DISTANT_FUTURE, InstantColumnAdapter.decode(encoded))
	}
}
