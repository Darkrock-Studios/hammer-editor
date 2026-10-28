package com.darkrockstudios.apps.hammer.base.http

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiProjectEntityTypeTest {
	@Test
	fun `Every type round trips through its string and int ids`() {
		for (type in ApiProjectEntity.Type.entries) {
			assertEquals(type, ApiProjectEntity.Type.fromString(type.toStringId()), type.name)
			assertEquals(type, ApiProjectEntity.Type.fromInt(type.id), type.name)
		}
	}

	@Test
	fun `Upload header form parses back to the same type`() {
		// The entity-type header is written with Type.name and parsed with fromString.
		for (type in ApiProjectEntity.Type.entries) {
			assertEquals(type.name.lowercase(), type.toStringId(), type.name)
			assertEquals(type, ApiProjectEntity.Type.fromString(type.name), type.name)
		}
	}
}
