package com.darkrockstudios.build

import kotlin.test.Test
import kotlin.test.assertEquals

class VersionCodeTest {

	@Test
	fun `SemVar parsing`() {
		val semVar = parseSemVar("1.2.3")
		assertEquals(1, semVar.major)
		assertEquals(2, semVar.minor)
		assertEquals(3, semVar.patch)
	}

	@Test(expected = IllegalStateException::class)
	fun `SemVar failed parsing`() {
		val semVar = parseSemVar("asd")
	}

	@Test
	fun `Create Version Code`() {
		val semVar = parseSemVar("1.2.3")

		var versionCode = semVar.createVersionCode(false, 1)
		assertEquals(102030001, versionCode, "Release: False Build: 1")

		versionCode = semVar.createVersionCode(true, 1)
		assertEquals(102040000, versionCode, "Release: True Build: 1")
	}

	@Test
	fun `Wear version code sits above every phone version code`() {
		val phoneCode = parseSemVar("9.99.99").createVersionCode(false, 9_999)

		assertEquals(1_999_999_999, wearVersionCode(phoneCode))
		assertEquals(1_102_030_001, wearVersionCode(102_030_001))
	}

	@Test(expected = IllegalArgumentException::class)
	fun `Wear version code rejects a phone code that reaches the offset`() {
		wearVersionCode(1_000_000_000)
	}
}