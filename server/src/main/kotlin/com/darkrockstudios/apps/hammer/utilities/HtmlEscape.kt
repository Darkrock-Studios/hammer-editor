package com.darkrockstudios.apps.hammer.utilities

/** Escapes [value] for HTML text or a double-quoted attribute. Single quotes are left as they are. */
fun escapeHtml(value: String): String = value
	.replace("&", "&amp;")
	.replace("\"", "&quot;")
	.replace("<", "&lt;")
	.replace(">", "&gt;")
