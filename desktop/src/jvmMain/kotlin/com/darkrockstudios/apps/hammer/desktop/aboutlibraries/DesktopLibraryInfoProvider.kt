package com.darkrockstudios.apps.hammer.desktop.aboutlibraries

import androidx.compose.ui.res.useResource
import com.darkrockstudios.apps.hammer.common.util.LibraryInfoProvider
import com.mikepenz.aboutlibraries.Libs

class DesktopLibraryInfoProvider : LibraryInfoProvider {
	override fun libJson(): Libs {
		val json = readJson()
		return Libs.Builder().withJson(json).build()
	}

	private fun readJson(): String {
		return useResource("aboutlibraries.json") {
			it.bufferedReader().readText()
		}
	}
}