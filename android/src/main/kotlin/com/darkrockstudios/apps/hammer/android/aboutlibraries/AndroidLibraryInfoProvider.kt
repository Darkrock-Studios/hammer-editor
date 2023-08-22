package com.darkrockstudios.apps.hammer.android.aboutlibraries

import android.content.Context
import com.darkrockstudios.apps.hammer.android.R
import com.darkrockstudios.apps.hammer.common.util.LibraryInfoProvider
import com.mikepenz.aboutlibraries.Libs
import java.io.BufferedReader
import java.io.InputStreamReader

class AndroidLibraryInfoProvider(private val context: Context) : LibraryInfoProvider {
	override fun getLibs(): Libs {
		val json = readJson()
		return Libs.Builder().withJson(json).build()
	}

	private fun readJson(): String {
		return BufferedReader(
			InputStreamReader(context.resources.openRawResource(R.raw.aboutlibraries))
		).lines().reduce("\n") { a: String, b: String -> a + b } ?: error("Unable to read About Libraries res")
	}
}