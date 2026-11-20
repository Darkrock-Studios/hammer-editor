package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.projectselection.settings.ReadOnlyDictionaryControl
import com.darkrockstudios.apps.hammer.common.util.Locale

/** The read-only dictionary row Android shows in the spell check settings. */
@Composable
internal fun SpellCheckDictionaryAndroidPreview() {
	Column(modifier = Modifier.padding(24.dp).widthIn(max = 560.dp)) {
		ReadOnlyDictionaryControl(selected = Locale.forLanguageTag("en-US"), enabled = true)
	}
}
