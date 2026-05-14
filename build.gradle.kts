import com.darkrockstudios.build.configureRelease
import com.darkrockstudios.build.registerLinuxDistributionTasks
import com.darkrockstudios.build.registerPublishTasks
import com.darkrockstudios.build.updateFlatpakFiles
import com.darkrockstudios.build.updateSnapcraftYaml
import com.darkrockstudios.build.writeChangelogMarkdown
import com.darkrockstudios.build.writeSemvar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

buildscript {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}

	dependencies {
		classpath(libs.kotlinx.atomicfu.plugin)
		classpath(libs.jetbrains.kover)
	}
}

val xlibs = extensions.getByType<VersionCatalogsExtension>().named("libs")

allprojects {
	repositories {
		mavenLocal()
		google()
		mavenCentral()
		maven("https://jitpack.io")
		// Jewel artifacts (transitive via nucleus.decorated-window-jewel) — IDEA-aligned snapshot versions
		maven("https://www.jetbrains.com/intellij-repository/snapshots")
	}

	tasks.withType<Test> {
		useJUnitPlatform()
	}

	// Compiler flags applied globally
	tasks.withType<KotlinCompilationTask<*>>().configureEach {
		compilerOptions {
			freeCompilerArgs.addAll(
				listOf(
					"-Xexpect-actual-classes",
					"-opt-in=kotlin.time.ExperimentalTime",
					"-opt-in=androidx.compose.material.ExperimentalMaterialApi",
					"-opt-in=androidx.compose.material3.ExperimentalMaterialApi",
					"-opt-in=androidx.compose.runtime.ExperimentalComposeApi",
					"-opt-in=com.arkivanov.decompose.ExperimentalDecomposeApi",
					"-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi",
				)
			)
		}
	}

	dependencies {
		enforcedPlatform(xlibs.findLibrary("junit.bom").get())
	}
}

plugins {
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.jetbrains.compose) apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.kotlin.serialization) apply false
	alias(libs.plugins.kotlin.parcelize) apply false
	alias(libs.plugins.kotlin.android) apply false
	alias(libs.plugins.android.kotlin.multiplatform.library) apply false
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.android.library) apply false
	alias(libs.plugins.compose.compiler) apply false
	//alias(libs.plugins.compose.report.generator) apply false
	alias(libs.plugins.buildconfig) apply false
	alias(libs.plugins.aboutlibraries.plugin) apply false
	alias(libs.plugins.aboutlibraries.plugin.android) apply false
	alias(libs.plugins.jetbrains.kover)
	alias(libs.plugins.kotlinx.atomicfu)
	alias(libs.plugins.flatpak.gradle.generator) apply false
}

dependencies {
	//kover(project(":base"))
	kover(project(":common"))
	kover(project(":server"))
}

kover {
	reports {
		total {

		}
	}
}

registerPublishTasks()
registerLinuxDistributionTasks(libs.versions.app.get())

val releasePreFlightChecks = tasks.register("releasePreFlightChecks") {
	doLast {
		fun runGit(vararg args: String): String {
			val process = ProcessBuilder(*args)
				.directory(project.rootDir)
				.start()
			val stdout = process.inputStream.bufferedReader().readText().trim()
			process.waitFor()
			return stdout
		}

		println("Fetching origin...")
		ProcessBuilder("git", "fetch", "origin").directory(project.rootDir).inheritIO().start().waitFor()

		// Check for unstaged/uncommitted changes
		val statusText = runGit("git", "status", "--porcelain")
		if (statusText.isNotEmpty()) {
			error(
				"Working tree has uncommitted changes. Please commit or stash them before preparing a release.\n$statusText"
			)
		}

		// Check if develop is behind origin/develop
		val developBehind = runGit("git", "rev-list", "--count", "develop..origin/develop").toIntOrNull() ?: 0
		if (developBehind > 0) {
			error("Local 'develop' is behind 'origin/develop' by $developBehind commit(s). Please pull before preparing a release.")
		}

		println("Pre-flight checks passed.")
	}
}

// Ensure flatpak generator runs after pre-flight checks
project(":desktop").tasks.configureEach {
	if (name == "flatpakGradleGenerator") {
		mustRunAfter(releasePreFlightChecks)
	}
}

tasks.register("prepareForRelease") {
	dependsOn(releasePreFlightChecks, ":desktop:flatpakGradleGenerator")
	doLast {
		val releaseInfo =
			configureRelease(libs.versions.app.get()) ?: error("Failed to configure new release")

		println("Creating new release")
		val versionCode = releaseInfo.semVar.createVersionCode(true, 0)

		// Write the new version number
		val versionsPath = "gradle/libs.versions.toml".replace("/", File.separator)
		val versionsFile = project.rootDir.resolve(versionsPath)
		writeSemvar(libs.versions.app.get(), releaseInfo.semVar, versionsFile)

		// Google Play has a hard limit of 500 characters
		val truncatedChangelog = if (releaseInfo.changeLog.length > 500) {
			"${releaseInfo.changeLog.take(480)}... and more"
		} else {
			releaseInfo.changeLog
		}

		// Write the Fastlane changelog file
		val rootDir: File = project.rootDir
		val changelogsPath =
			"fastlane/metadata/android/en-US/changelogs".replace("/", File.separator)
		val changeLogsDir = rootDir.resolve(changelogsPath)
		val changeLogFile = File(changeLogsDir, "$versionCode.txt")
		changeLogFile.writeText(truncatedChangelog)
		println("Changelog for version ${releaseInfo.semVar} written to $changelogsPath/$versionCode.txt")

		// Write the Global changelog file
		val globalChangelogFile = File("${project.rootDir}/CHANGELOG.md")
		writeChangelogMarkdown(releaseInfo, globalChangelogFile)

		// Update snapcraft.yaml with new version and JVM version
		val snapcraftPath = "snap/snapcraft.yaml".replace("/", File.separator)
		val snapcraftFile = project.rootDir.resolve(snapcraftPath)
		val jvmVersion = libs.versions.jvm.get()
		updateSnapcraftYaml(releaseInfo.semVar, jvmVersion, snapcraftFile)

		// Update Flatpak manifest and metainfo with new version and JVM version
		val flatpakManifestPath = "flatpak/studio.darkrock.hammer.yaml".replace("/", File.separator)
		val flatpakManifestFile = project.rootDir.resolve(flatpakManifestPath)
		val flatpakMetainfoPath = "flatpak/studio.darkrock.hammer.metainfo.xml".replace("/", File.separator)
		val flatpakMetainfoFile = project.rootDir.resolve(flatpakMetainfoPath)
		updateFlatpakFiles(releaseInfo.semVar, jvmVersion, flatpakManifestFile, flatpakMetainfoFile, releaseInfo.changeLog)

		fun git(vararg args: String) {
			val cmd = listOf("git") + args.toList()
			println("> ${cmd.joinToString(" ")}")
			val process = ProcessBuilder(cmd)
				.directory(project.rootDir)
				.redirectErrorStream(true)
				.start()
			val output = process.inputStream.bufferedReader().readText()
			val exitCode = process.waitFor()
			if (exitCode != 0) {
				error("Git command failed: ${cmd.joinToString(" ")}\n${output.trim()}")
			}
		}

		// Commit the changes to the repo
		git("add", changeLogFile.absolutePath)
		git("add", versionsFile.absolutePath)
		git("add", globalChangelogFile.absolutePath)
		git("add", snapcraftFile.absolutePath)
		git("add", flatpakManifestFile.absolutePath)
		git("add", flatpakMetainfoFile.absolutePath)
		val flatpakSourcesPath = "flatpak/flatpak-sources.json".replace("/", File.separator)
		val flatpakSourcesFile = project.rootDir.resolve(flatpakSourcesPath)
		git("add", flatpakSourcesFile.absolutePath)
		git("commit", "-m", "Prepared for release: v${releaseInfo.semVar}")

		// Switch to release and reset to origin/release HEAD
		git("checkout", "release")
		git("reset", "--hard", "origin/release")
		git("merge", "-X", "theirs", "develop")

		// Create the release tag
		git("tag", "-a", "v${releaseInfo.semVar}", "-m", releaseInfo.changeLog)

		// Push and begin the release process
		git("push", "origin", "develop", "release")
		git("push", "origin", "--tags")

		// Leave the repo back on develop
		git("checkout", "develop")
	}
}

tasks.register("backoutLastRelease") {
	doLast {
		val version = libs.versions.app.get()
		val tagName = "v$version"

		println("Attempting to back out release $tagName...")

		fun gitSafe(vararg args: String): Boolean {
			val cmd = listOf("git") + args.toList()
			println("> ${cmd.joinToString(" ")}")
			val process = ProcessBuilder(cmd)
				.directory(project.rootDir)
				.redirectErrorStream(true)
				.start()
			val output = process.inputStream.bufferedReader().readText()
			val exitCode = process.waitFor()
			if (exitCode != 0) {
				println("  (failed: ${output.trim()})")
			}
			return exitCode == 0
		}

		// Make sure we're on develop
		gitSafe("checkout", "develop")

		// Check if HEAD commit is the release commit
		val headProcess = ProcessBuilder("git", "log", "-1", "--format=%s")
			.directory(project.rootDir).start()
		val headMessage = headProcess.inputStream.bufferedReader().readText().trim()
		headProcess.waitFor()

		if (headMessage == "Prepared for release: $tagName") {
			println("Resetting develop to before release commit...")
			gitSafe("reset", "--hard", "HEAD~1")
		} else {
			println("HEAD commit is not the release commit, discarding any uncommitted changes...")
			println("  HEAD: $headMessage")
			gitSafe("checkout", "--", ".")
		}

		// Delete tag locally if it exists
		val tagExists = ProcessBuilder("git", "rev-parse", tagName)
			.directory(project.rootDir)
			.start()
			.waitFor() == 0

		if (tagExists) {
			println("Deleting local tag $tagName...")
			gitSafe("tag", "-d", tagName)
		} else {
			println("Tag $tagName does not exist locally, skipping.")
		}

		// Reset release branch to origin/release
		println("Resetting release branch to origin/release...")
		gitSafe("checkout", "release")
		gitSafe("reset", "--hard", "origin/release")

		// Return to develop
		gitSafe("checkout", "develop")

		println("Backout complete. Remote was NOT modified — if the push already went through, you'll need to force-push manually.")
	}
}

tasks.register("revertLastRelease") {
	doLast {
		val version = libs.versions.app.get()
		val tagName = "v$version"

		println("Reverting release $tagName from local and remote...")

		fun git(vararg args: String) {
			val cmd = listOf("git") + args.toList()
			println("> ${cmd.joinToString(" ")}")
			val process = ProcessBuilder(cmd)
				.directory(project.rootDir)
				.redirectErrorStream(true)
				.start()
			val output = process.inputStream.bufferedReader().readText()
			val exitCode = process.waitFor()
			if (exitCode != 0) error("Git command failed: ${cmd.joinToString(" ")}\n${output.trim()}")
		}

		fun gitSafe(vararg args: String): Boolean {
			val cmd = listOf("git") + args.toList()
			println("> ${cmd.joinToString(" ")}")
			val process = ProcessBuilder(cmd)
				.directory(project.rootDir)
				.redirectErrorStream(true)
				.start()
			val output = process.inputStream.bufferedReader().readText()
			val exitCode = process.waitFor()
			if (exitCode != 0) println("  (skipped: ${output.trim()})")
			return exitCode == 0
		}

		fun gitOutput(vararg args: String): String {
			val process = ProcessBuilder(listOf("git") + args.toList())
				.directory(project.rootDir)
				.redirectErrorStream(true)
				.start()
			val output = process.inputStream.bufferedReader().readText().trim()
			process.waitFor()
			return output
		}

		git("fetch", "origin")

		// Validate develop HEAD is the release commit we expect to undo
		git("checkout", "develop")
		val developHead = gitOutput("log", "-1", "--format=%s")
		if (developHead != "Prepared for release: $tagName") {
			error("develop HEAD is '$developHead', expected 'Prepared for release: $tagName'. Cannot safely revert.")
		}

		// Validate release HEAD is a merge commit (has 2 parents)
		git("checkout", "release")
		val releaseParents = gitOutput("log", "-1", "--format=%P").split("\\s+".toRegex()).filter { it.isNotEmpty() }
		if (releaseParents.size < 2) {
			error("release HEAD is not a merge commit. Cannot safely revert.")
		}

		// Reset release to its pre-merge state (first parent of the merge commit)
		println("Resetting release to pre-merge state...")
		git("reset", "--hard", "HEAD^1")
		git("push", "--force", "origin", "release")

		// Reset develop to before the release commit
		println("Resetting develop to pre-release state...")
		git("checkout", "develop")
		git("reset", "--hard", "HEAD~1")
		git("push", "--force", "origin", "develop")

		// Delete tag from remote then local
		println("Deleting tag $tagName...")
		gitSafe("push", "origin", "--delete", tagName)
		gitSafe("tag", "-d", tagName)

		println("Done. $tagName has been fully reverted on local and remote.")
	}
}
