package com.darkrockstudios.apps.hammer.common.data.projectdata

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectDataMergeTest {

	@Test
	fun `merged dictionary words are the union of both sides`() {
		val local = ProjectData(dictionaryWords = setOf("Kvothe"))
		val server = ProjectData(dictionaryWords = setOf("Denna", "Imre"))

		assertEquals(setOf("Kvothe", "Denna", "Imre"), mergeDictionaryWords(local, server))
	}

	@Test
	fun `merging keeps a word this build would not accept as input`() {
		// The merged set is uploaded, so stripping it here would delete a newer build's word
		// server-side for every other device.
		val local = ProjectData(dictionaryWords = setOf("Kvothe"))
		val server = ProjectData(dictionaryWords = setOf("Chandrian sign"))

		assertEquals(setOf("Kvothe", "Chandrian sign"), mergeDictionaryWords(local, server))
	}

	@Test
	fun `differsOutsideDictionary ignores the dictionary and nothing else`() {
		val base = ProjectData(authorName = "Pat", dictionaryWords = setOf("a"))

		assertFalse(base.differsOutsideDictionary(base.copy(dictionaryWords = setOf("b"))))
		assertTrue(base.differsOutsideDictionary(base.copy(authorName = "Sam")))
	}

	@Test
	fun `reapplyLocalEdits keeps changed fields and takes the rest from incoming`() {
		val base = ProjectData(authorName = "Pat", language = "en")
		val local = base.copy(authorName = "Edited")
		val incoming = ProjectData(authorName = "Server", language = "fr")

		assertEquals(
			ProjectData(authorName = "Edited", language = "fr"),
			reapplyLocalEdits(base, local, incoming),
		)
	}

	@Test
	fun `reapplyLocalEdits carries dictionary additions and removals as a delta`() {
		val base = ProjectData(dictionaryWords = setOf("keep", "drop"))
		val local = base.copy(dictionaryWords = setOf("keep", "added"))
		val incoming = ProjectData(dictionaryWords = setOf("keep", "drop", "server"))

		assertEquals(
			setOf("keep", "server", "added"),
			reapplyLocalEdits(base, local, incoming).dictionaryWords,
		)
	}

	@Test
	fun `reapplyLocalEdits keeps a local cover change`() {
		val base = ProjectData(coverImageId = 1)
		val local = base.copy(coverImageId = 2)
		val incoming = ProjectData(coverImageId = 3)

		assertEquals(2, reapplyLocalEdits(base, local, incoming).coverImageId)
	}

	@Test
	fun `reapplyLocalEdits keeps a locally cleared cover`() {
		val base = ProjectData(coverImageId = 1)
		val local = base.copy(coverImageId = null)
		val incoming = ProjectData(coverImageId = 3)

		assertEquals(null, reapplyLocalEdits(base, local, incoming).coverImageId)
	}

	@Test
	fun `reapplyLocalEdits takes the incoming cover when unchanged locally`() {
		val base = ProjectData(coverImageId = 1, authorName = "Pat")
		val local = base.copy(authorName = "Edited")
		val incoming = ProjectData(coverImageId = 3)

		assertEquals(3, reapplyLocalEdits(base, local, incoming).coverImageId)
	}
}
