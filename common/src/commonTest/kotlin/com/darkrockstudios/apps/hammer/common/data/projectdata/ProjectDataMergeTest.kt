package com.darkrockstudios.apps.hammer.common.data.projectdata

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectDataMergeTest {

	@Test
	fun `merged dictionary words are the cleaned union`() {
		val local = ProjectData(dictionaryWords = setOf("Kvothe", "two words"))
		val server = ProjectData(dictionaryWords = setOf("Denna", " Imre "))

		assertEquals(setOf("Kvothe", "Denna", "Imre"), mergeDictionaryWords(local, server))
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
}
