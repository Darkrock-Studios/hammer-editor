package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.projectselection.about.LibrariesCard
import com.mikepenz.aboutlibraries.Libs

/** The open source libraries dialog, over a scrim. */
@Composable
internal fun LibrariesScrimPreview() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		LibrariesCard(libs = previewLibs, onClose = {}, initiallyOpen = "com.arkivanov.decompose:decompose")
	}
}

internal val previewLibs: Libs by lazy { Libs.Builder().withJson(PREVIEW_LIBRARIES_JSON).build() }

private const val PREVIEW_LIBRARIES_JSON = """
{
  "libraries": [
    {"uniqueId": "com.arkivanov.decompose:decompose", "artifactVersion": "3.5.0", "name": "Decompose",
     "description": "Kotlin Multiplatform lifecycle-aware business logic components with routing functionality and pluggable UI",
     "website": "https://github.com/arkivanov/Decompose", "developers": [{"name": "Arkadii Ivanov"}], "licenses": ["Apache-2.0"]},
    {"uniqueId": "com.arkivanov.essenty:lifecycle", "artifactVersion": "2.5.0", "name": "Essenty",
     "website": "https://github.com/arkivanov/Essenty", "developers": [{"name": "Arkadii Ivanov"}], "licenses": ["Apache-2.0"]},
    {"uniqueId": "io.insert-koin:koin-core", "artifactVersion": "4.1.1", "name": "Koin",
     "description": "Pragmatic lightweight dependency injection framework for Kotlin",
     "website": "https://insert-koin.io", "developers": [{"name": "Kotzilla"}], "licenses": ["Apache-2.0"]},
    {"uniqueId": "io.ktor:ktor-client-core", "artifactVersion": "3.3.1", "name": "Ktor",
     "website": "https://ktor.io", "developers": [{"name": "JetBrains"}], "licenses": ["Apache-2.0"]},
    {"uniqueId": "com.squareup.okio:okio", "artifactVersion": "3.16.0", "name": "Okio",
     "description": "A modern I/O library for Android, Java, and Kotlin Multiplatform.",
     "website": "https://github.com/square/okio/", "developers": [{"name": "Square, Inc."}], "licenses": ["Apache-2.0"]},
    {"uniqueId": "com.fleeksoft.ksoup:ksoup", "artifactVersion": "0.2.5", "name": "ksoup",
     "website": "https://github.com/fleeksoft/ksoup", "developers": [{"name": "Sabeeh Ul Hussnain"}], "licenses": ["MIT"]},
    {"uniqueId": "io.github.aakira:napier", "artifactVersion": "2.7.1", "name": "Napier",
     "description": "Logging library for Kotlin Multiplatform",
     "website": "https://github.com/AAkira/Napier", "developers": [{"name": "AAkira"}], "licenses": ["Apache-2.0"]}
  ],
  "licenses": {
    "Apache-2.0": {"name": "Apache License 2.0", "url": "https://spdx.org/licenses/Apache-2.0.html", "hash": "Apache-2.0", "spdxId": "Apache-2.0"},
    "MIT": {"name": "MIT License", "url": "https://spdx.org/licenses/MIT.html", "hash": "MIT", "spdxId": "MIT"}
  }
}
"""
