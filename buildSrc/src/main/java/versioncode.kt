package com.darkrockstudios.build

import java.util.regex.Pattern

fun getVersionCode(semVarStr: String): Int {
	val buildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull()
	val isRelease = (System.getenv("RELEASE_BUILD")?.toBoolean() == true)
	val semVar = parseSemVar(semVarStr)
	val versionCode = semVar.createVersion(isRelease, buildNumber)

	return versionCode
}

private data class SemVar(
	val major: Int,
	val minor: Int,
	val patch: Int,
) {
	fun createVersion(
		isRelease: Boolean,
		buildNumber: Int?
	): Int {
		var versionCode = buildNumber?.mod(10000) ?: 0

		if (major >= 100 || minor >= 100 || patch >= 100 || (buildNumber ?: 0) >= 10000) {
			throw IllegalArgumentException("Version component out of range")
		}

		// Increment by one for release builds
		val releasePatch = if (isRelease) {
			patch + 1
		} else {
			patch
		}

		versionCode += (releasePatch * 100)
		versionCode += (minor * 10_000)
		versionCode += (major * 1_000_000)

		return versionCode
	}
}

private fun parseSemVar(semVarStr: String): SemVar {
	val semVarPattern = Pattern.compile("""^(\d+)\.(\d+)\.(\d+)$""")
	val matcher = semVarPattern.matcher(semVarStr)
	matcher.find()

	return SemVar(
		major = matcher.group(1).toInt(),
		minor = matcher.group(2).toInt(),
		patch = matcher.group(3).toInt(),
	)
}