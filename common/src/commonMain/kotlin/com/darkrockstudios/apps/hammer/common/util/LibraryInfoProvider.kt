package com.darkrockstudios.apps.hammer.common.util

import com.mikepenz.aboutlibraries.Libs

interface LibraryInfoProvider {
	fun getLibs(): Libs
}