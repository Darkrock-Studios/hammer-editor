package com.darkrockstudios.build

import java.util.regex.Pattern

fun getVersionCode(semVarStr: String): Int {
	val version = if (System.getenv("RELEASE_BUILD")?.toBoolean() == true) {
		ReleaseVersion(parseSemvar(semVarStr))
	} else {
		DebugVersion(parseSemvar(semVarStr))
	}

	return version.getVersionCode()
}

private abstract class AppVersion {
	abstract val semvar: SemVar

	abstract fun getVersionCode(): Int

	protected fun createVersion(isRelease: Boolean, buildNumber: Int?): Int {
		var versionCode = buildNumber?.mod(100) ?: 0

		if (isRelease) {
			versionCode += 1_000
		}

		versionCode += (semvar.patch * 10_000)

		versionCode += (semvar.minor * 1_000_000)

		versionCode += (semvar.major * 100_000_000)

		return versionCode
	}
}

private data class ReleaseVersion(
	override val semvar: SemVar
) : AppVersion() {
	override fun getVersionCode(): Int = createVersion(true, 0)
}

private data class DebugVersion(
	override val semvar: SemVar,
) : AppVersion() {
	override fun getVersionCode(): Int =
		createVersion(false, System.getenv("BUILD_NUMBER")?.toIntOrNull())
}

private data class SemVar(
	val major: Int,
	val minor: Int,
	val patch: Int,
)

private fun parseSemvar(semVarStr: String): SemVar {
	val semVarPattern = Pattern.compile("""^(\d+)\.(\d+)\.(\d+)$""")
	val matcher = semVarPattern.matcher(semVarStr)
	matcher.find()

	return SemVar(
		major = matcher.group(1).toInt(),
		minor = matcher.group(2).toInt(),
		patch = matcher.group(3).toInt(),
	)
}