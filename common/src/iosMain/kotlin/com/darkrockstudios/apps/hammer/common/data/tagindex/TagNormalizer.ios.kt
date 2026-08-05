package com.darkrockstudios.apps.hammer.common.data.tagindex

import platform.Foundation.NSCharacterSet
import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCanonicalMapping

internal actual fun normalizeTagForm(text: String): String =
	(text as NSString).precomposedStringWithCanonicalMapping

/** Foundation's alphanumeric set is Unicode categories L, M and N, which all read as tag content. */
private val alphanumeric = NSCharacterSet.alphanumericCharacterSet

internal actual fun isSupplementaryLetterOrDigit(codePoint: Int): Boolean =
	alphanumeric.longCharacterIsMember(codePoint.toUInt())
