package com.darkrockstudios.apps.hammer.common.util

/**
 * This text as a search compares it: lower case, with the accents of Latin letters dropped, so "sao"
 * finds "São".
 */
fun String.foldedForSearch(): String = buildString(length) {
	for (c in this@foldedForSearch.lowercase()) {
		val at = if (c < FIRST_ACCENTED) -1 else ACCENTED.indexOf(c)
		append(if (at < 0) c else UNACCENTED[at])
	}
}

/** Whether [needle] is in this text, ignoring case and the accents of Latin letters. */
fun String.containsFolded(needle: String): Boolean = needle.foldedForSearch() in foldedForSearch()

// Index for index: each letter of ACCENTED folds to the letter at its place in UNACCENTED.
private const val ACCENTED = "àáâãäåçèéêëìíîïñòóôõöùúûüýÿāăąćĉċčďēĕėęěĝğġģĥĩīĭįĵķĺļľńņňōŏőŕŗřśŝşšţťũūŭůűųŵŷźżžơưǎǐǒǔǖǘǚǜǟǡǧǩǫǭǰǵǹǻȁȃȅȇȉȋȍȏȑȓȕȗșțȟȧȩȫȭȯȱȳøłđıß"
private const val UNACCENTED = "aaaaaaceeeeiiiinooooouuuuyyaaaccccdeeeeegggghiiiijklllnnnooorrrssssttuuuuuuwyzzzouaiouuuuuaagkoojgnaaaeeiioorruusthaeooooyoldis"
private const val FIRST_ACCENTED = '\u00DF'
