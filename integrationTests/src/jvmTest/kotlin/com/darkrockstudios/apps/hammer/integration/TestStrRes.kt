package com.darkrockstudios.apps.hammer.integration

import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.mockk.coEvery
import io.mockk.mockk

/**
 * `StrRes` ultimately calls into Compose Resources, which isn't initialized in a
 * plain JVM test JVM. Sync logs strings only on failure paths; we don't care
 * about their content, only that they don't blow up. A relaxed mockk satisfies that.
 */
fun testStrRes(): StrRes = mockk<StrRes>(relaxed = true).also { mock ->
	coEvery { mock.get(any()) } returns "[stub]"
	coEvery { mock.get(any(), *anyVararg()) } returns "[stub]"
}
