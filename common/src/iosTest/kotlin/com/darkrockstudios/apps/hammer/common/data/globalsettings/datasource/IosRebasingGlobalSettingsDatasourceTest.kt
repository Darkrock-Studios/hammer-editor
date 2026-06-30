package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import kotlin.test.Test
import kotlin.test.assertEquals

class IosRebasingGlobalSettingsDatasourceTest {

	private val liveDocuments =
		"/var/mobile/Containers/Data/Application/NEW-UUID/Documents"

	@Test
	fun pathAlreadyUnderLiveDocumentsIsLeftUnchanged() {
		val current = "$liveDocuments/HammerProjects"
		assertEquals(current, rebaseProjectsDirectory(current, liveDocuments))
	}

	@Test
	fun documentsRootItselfIsLeftUnchanged() {
		assertEquals(liveDocuments, rebaseProjectsDirectory(liveDocuments, liveDocuments))
	}

	@Test
	fun staleContainerPathIsReanchoredKeepingTheSuffix() {
		val stale =
			"/var/mobile/Containers/Data/Application/OLD-UUID/Documents/HammerProjects"
		assertEquals(
			"$liveDocuments/HammerProjects",
			rebaseProjectsDirectory(stale, liveDocuments),
		)
	}

	@Test
	fun nestedSuffixIsPreservedDuringRebase() {
		val stale =
			"/var/mobile/Containers/Data/Application/OLD-UUID/Documents/HammerProjects/MyBook"
		assertEquals(
			"$liveDocuments/HammerProjects/MyBook",
			rebaseProjectsDirectory(stale, liveDocuments),
		)
	}

	@Test
	fun pathWithoutADocumentsMarkerFallsBackToTheDefaultProjectsDir() {
		assertEquals(
			"$liveDocuments/HammerProjects",
			rebaseProjectsDirectory("/some/totally/foreign/path", liveDocuments),
		)
	}
}
