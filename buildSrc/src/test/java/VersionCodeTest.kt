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

	@Test
	fun `Create Version Code`() {
		val semVar = parseSemVar("1.2.3")

		var versionCode = semVar.createVersion(false, 1)
		assertEquals(102030001, versionCode, "Release: False Build: 1")

		versionCode = semVar.createVersion(true, 1)
		assertEquals(102040000, versionCode, "Release: True Build: 1")
	}
}