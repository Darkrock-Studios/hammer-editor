package com.darkrockstudios.apps.hammer.common.data.tagindex

import java.text.Normalizer

internal actual fun normalizeTagForm(text: String): String =
	if (Normalizer.isNormalized(text, Normalizer.Form.NFC)) text
	else Normalizer.normalize(text, Normalizer.Form.NFC)

internal actual fun isSupplementaryLetterOrDigit(codePoint: Int): Boolean =
	Character.isLetterOrDigit(codePoint)
