package com.darkrockstudios.apps.hammer.common.utils

import com.darkrockstudios.libs.platformspellchecker.PlatformSpellChecker
import com.darkrockstudios.texteditor.spellcheck.adapters.PlatformEditorSpellChecker

fun PlatformSpellChecker?.toEditorSpellChecker(): PlatformEditorSpellChecker? =
	this?.let { PlatformEditorSpellChecker(it) }