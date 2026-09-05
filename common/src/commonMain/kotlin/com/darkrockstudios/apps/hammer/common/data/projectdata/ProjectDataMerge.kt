package com.darkrockstudios.apps.hammer.common.data.projectdata

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData

/** Dictionary words never need a decision: a conflict keeps both sides. */
fun mergeDictionaryWords(local: ProjectData, server: ProjectData): Set<String> =
	local.dictionaryWords + server.dictionaryWords

/** True when something other than the auto-merged dictionary differs. */
fun ProjectData.differsOutsideDictionary(other: ProjectData): Boolean =
	copy(dictionaryWords = emptySet()) != other.copy(dictionaryWords = emptySet())
