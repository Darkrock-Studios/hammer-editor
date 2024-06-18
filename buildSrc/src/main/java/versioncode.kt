package com.darkrockstudios.build

import java.util.regex.Pattern

fun getVersionCode(semVarStr: String): Int {
	val buildNumber = (System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 0)
	val isRelease = (System.getenv("RELEASE_BUILD")?.toBoolean() == true)
	val semVar = parseSemVar(semVarStr)
	val versionCode = semVar.createVersionCode(isRelease, buildNumber)

	return versionCode
}

data class SemVar(
	val major: Int,
	val minor: Int,
	val patch: Int,
) {
	private val MAX_BUILDNUM = 10_000

	fun createVersionCode(
		isRelease: Boolean,
		buildNumber: Int
	): Int {
		var versionCode = if (isRelease) {
			0
		} else {
			buildNumber.mod(MAX_BUILDNUM)
		}

		if (major >= 100 || minor >= 100 || patch >= 100 || versionCode >= MAX_BUILDNUM) {
			throw IllegalArgumentException("Version component out of range")
		}

		// Increment by one for release builds
		val releasePatch = patch + if (isRelease) 1 else 0

		versionCode += (releasePatch * 10_000)
		versionCode += (minor * 1_000_000)
		versionCode += (major * 100_000_000)

		return versionCode
	}

	fun incrementForRelease(type: ReleaseType): SemVar {
		return when (type) {
			ReleaseType.MAJOR -> SemVar(
				major = major + 1,
				minor = 0,
				patch = 0,
			)

			ReleaseType.MINOR -> SemVar(
				major = major,
				minor = minor + 1,
				patch = 0,
			)

			ReleaseType.PATCH -> SemVar(
				major = major,
				minor = minor,
				patch = patch + 1,
			)
		}
	}

	override fun toString(): String {
		return "$major.$minor.$patch"
	}

	enum class ReleaseType {
		MAJOR, MINOR, PATCH
	}
}

fun parseSemVar(semVarStr: String): SemVar {
	val semVarPattern = Pattern.compile("""^(\d+)\.(\d+)\.(\d+)$""")
	val matcher = semVarPattern.matcher(semVarStr)

	if (matcher.find().not()) {
		error("Invalid SemVar string: $semVarStr")
	}

	return SemVar(
		major = matcher.group(1).toInt(),
		minor = matcher.group(2).toInt(),
		patch = matcher.group(3).toInt(),
	)
}