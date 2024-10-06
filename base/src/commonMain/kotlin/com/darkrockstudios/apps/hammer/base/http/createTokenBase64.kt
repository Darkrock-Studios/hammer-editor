package com.darkrockstudios.apps.hammer.base.http

import kotlin.io.encoding.Base64

fun createTokenBase64(): Base64 {
	return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
}