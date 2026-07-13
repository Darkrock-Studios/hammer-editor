import com.darkrockstudios.build.configureRelease
import com.darkrockstudios.build.extractLatestChangelog
import com.darkrockstudios.build.isPlatformReleaseTag
import com.darkrockstudios.build.registerLinuxDistributionTasks
import com.darkrockstudios.build.registerPublishTasks
import com.darkrockstudios.build.updateFlatpakFiles
import com.darkrockstudios.build.updateIosShortVersion
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
}

dependencies {
	//kover(project(":base"))
	kover(project(":common"))
	kover(project(":server"))
}

kover {
	reports {
		filters {
			excludes {
				// Generated Compose Multiplatform resource accessors.
				classes(
					"com.darkrockstudios.apps.hammer.String*",
					"com.darkrockstudios.apps.hammer.Drawable*",
					"com.darkrockstudios.apps.hammer.Font*",
					"com.darkrockstudios.apps.hammer.Res",
					"*ResourceCollectorsKt",
				)
				// Generated SQLDelight code: query wrappers and DB impls.
				packages("com.darkrockstudios.apps.hammer.legacy")
				classes(
					"*Queries",
					"*.ServerDatabaseImpl",
					"*.LegacySqliteDatabaseImpl",
					"*.LegacySqliteDatabaseImplKt",
				)
			}
		}
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

tasks.register("prepareForRelease") {
	dependsOn(releasePreFlightChecks)
	doLast {
		val lastReleaseChangelog = extractLatestChangelog(File("${project.rootDir}/CHANGELOG.md"))
		val releaseInfo =
			configureRelease(libs.versions.app.get(), lastReleaseChangelog)
				?: error("Failed to configure new release")

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

		// Apple App Store has a 4000 character limit for release notes
		val truncatedAppleChangelog = if (releaseInfo.changeLog.length > 4000) {
			"${releaseInfo.changeLog.take(3980)}... and more"
		} else {
			releaseInfo.changeLog
		}

		// Write the macOS App Store release notes
		val macReleaseNotesPath = "fastlane/metadata/osx/en-US".replace("/", File.separator)
		val macReleaseNotesDir = rootDir.resolve(macReleaseNotesPath)
		macReleaseNotesDir.mkdirs()
		val macReleaseNotesFile = File(macReleaseNotesDir, "release_notes.txt")
		macReleaseNotesFile.writeText(truncatedAppleChangelog)
		println("macOS release notes written to $macReleaseNotesPath/release_notes.txt")

		// Write the iOS App Store release notes
		val iosReleaseNotesPath = "fastlane/metadata/ios/en-US".replace("/", File.separator)
		val iosReleaseNotesDir = rootDir.resolve(iosReleaseNotesPath)
		iosReleaseNotesDir.mkdirs()
		val iosReleaseNotesFile = File(iosReleaseNotesDir, "release_notes.txt")
		iosReleaseNotesFile.writeText(truncatedAppleChangelog)
		println("iOS release notes written to $iosReleaseNotesPath/release_notes.txt")

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

		// Keep the iOS marketing version in sync with the semver. macOS pulls this
		// from Compose's packageVersion automatically; iOS has no such hook.
		val iosInfoPlistPath = "ios/ios/Info.plist".replace("/", File.separator)
		val iosInfoPlistFile = project.rootDir.resolve(iosInfoPlistPath)
		updateIosShortVersion(releaseInfo.semVar, iosInfoPlistFile)

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
		git("add", macReleaseNotesFile.absolutePath)
		git("add", iosReleaseNotesFile.absolutePath)
		git("add", versionsFile.absolutePath)
		git("add", globalChangelogFile.absolutePath)
		git("add", snapcraftFile.absolutePath)
		git("add", flatpakManifestFile.absolutePath)
		git("add", flatpakMetainfoFile.absolutePath)
		git("add", iosInfoPlistFile.absolutePath)
		git("commit", "-m", "Prepared for release: v${releaseInfo.semVar}")

		// Merge develop into release in a throwaway worktree, never checking
		// release out in the main tree: the running daemon holds gradle-wrapper.jar
		// open, so a checkout that replaces it fails on Windows (unable to unlink).
		fun gitIn(dir: File, vararg args: String): Int {
			val cmd = listOf("git") + args.toList()
			println("> (${dir.name}) ${cmd.joinToString(" ")}")
			val process = ProcessBuilder(cmd)
				.directory(dir)
				.redirectErrorStream(true)
				.start()
			val output = process.inputStream.bufferedReader().readText()
			val exitCode = process.waitFor()
			if (output.isNotBlank()) println(output.trim())
			return exitCode
		}

		git("branch", "-f", "release", "origin/release")

		val releaseWorktree = File(project.rootDir, "build/release-merge")
		// Clear any leftover worktree from a previous failed run (ignore failure).
		gitIn(project.rootDir, "worktree", "remove", "--force", releaseWorktree.absolutePath)
		if (gitIn(project.rootDir, "worktree", "add", releaseWorktree.absolutePath, "release") != 0) {
			error("Failed to create release worktree at ${releaseWorktree.absolutePath}")
		}
		try {
			if (gitIn(releaseWorktree, "merge", "-X", "theirs", "develop") != 0) {
				error("Failed to merge develop into release")
			}
		} finally {
			gitIn(project.rootDir, "worktree", "remove", "--force", releaseWorktree.absolutePath)
		}

		// Tag the merge commit explicitly; the main tree stays on develop.
		val tagMessageFile = File(project.rootDir, "build/release-tag-message.txt")
		tagMessageFile.parentFile.mkdirs()
		tagMessageFile.writeText(releaseInfo.changeLog)
		git("tag", "-a", releaseInfo.tag, "-F", tagMessageFile.absolutePath, "release")

		// Push the branches and only this release's tag. Pushing --tags would try
		// to sync every stale local tag and fail when one already exists on origin.
		git("push", "origin", "develop", "release")
		git("push", "origin", "refs/tags/${releaseInfo.tag}")
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

		// Lists every local tag for this version — both the bare `vX.Y.Z` (full
		// release) and any `vX.Y.Z+platform+...` (partial release) variants.
		// Filtered through `isPlatformReleaseTag` so unrelated tags that share
		// the prefix (`vX.Y.Z+rc1`, `vX.Y.Z+sbom`) aren't included.
		fun findReleaseTags(): List<String> {
			val proc = ProcessBuilder("git", "tag", "-l", tagName, "$tagName+*")
				.directory(project.rootDir)
				.start()
			val tags = proc.inputStream.bufferedReader().readLines().filter { it.isNotBlank() }
			proc.waitFor()
			return tags.filter { isPlatformReleaseTag(it, tagName) }
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

		// Delete every tag for this version (exact + any +platform suffixes)
		val matchingTags = findReleaseTags()
		if (matchingTags.isEmpty()) {
			println("No local tags matching $tagName or $tagName+* found, skipping.")
		} else {
			matchingTags.forEach { tag ->
				println("Deleting local tag $tag...")
				gitSafe("tag", "-d", tag)
			}
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
			val cmd = listOf("git") + args.toList()
			val process = ProcessBuilder(cmd)
				.directory(project.rootDir)
				.redirectErrorStream(true)
				.start()
			val output = process.inputStream.bufferedReader().readText().trim()
			val exitCode = process.waitFor()
			// Throw on non-zero — otherwise a failing `ls-remote` (network
			// blip, auth expiry) silently returns its error text on stdout and
			// findReleaseTags() treats it as "no remote tags", skipping remote
			// deletion and leaving the suffix tag on origin.
			if (exitCode != 0) error("Git command failed: ${cmd.joinToString(" ")}\n${output}")
			return output
		}

		// Lists every tag for this version on local OR remote — bare `vX.Y.Z`
		// plus any `vX.Y.Z+platform+...` partial-release variants. Filtered
		// through `isPlatformReleaseTag` so unrelated tags that share the
		// prefix (`vX.Y.Z+rc1`, `vX.Y.Z+sbom`) aren't included.
		fun findReleaseTags(): List<String> {
			val local = gitOutput("tag", "-l", tagName, "$tagName+*").lines()
			val remote = gitOutput("ls-remote", "--tags", "origin", tagName, "$tagName+*")
				.lines()
				.mapNotNull { line ->
					// Format: "<sha>\trefs/tags/<name>" (and optionally "...^{}" for peeled tag refs)
					line.substringAfter("refs/tags/", "").removeSuffix("^{}").ifBlank { null }
				}
			return (local + remote)
				.filter { it.isNotBlank() }
				.distinct()
				.filter { isPlatformReleaseTag(it, tagName) }
		}

		git("fetch", "origin")

		// Validate develop HEAD is the release commit we expect to undo
		git("checkout", "develop")
		val developHead = gitOutput("log", "-1", "--format=%s")
		if (developHead != "Prepared for release: $tagName") {
			error("develop HEAD is '$developHead', expected 'Prepared for release: $tagName'. Cannot safely revert.")
		}

		// Validate release HEAD is a merge commit (has 2 parents). Read the ref
		// directly rather than checking it out — see below for why we must not
		// materialize the release working tree.
		val releaseParents = gitOutput("log", "-1", "--format=%P", "refs/heads/release").split("\\s+".toRegex()).filter { it.isNotEmpty() }
		if (releaseParents.size < 2) {
			error("release HEAD is not a merge commit. Cannot safely revert.")
		}

		// Reset release to its pre-merge state (first parent of the merge commit).
		// Move the ref with `git branch -f` instead of `checkout release` +
		// `reset --hard`: on Windows the running Gradle daemon holds
		// gradle/wrapper/gradle-wrapper.jar open, and the merge changes that jar,
		// so a working-tree reset fails with "unable to unlink ... Invalid argument".
		// Updating the ref without ever checking release out never touches the file.
		println("Resetting release to pre-merge state...")
		git("branch", "-f", "release", releaseParents[0])
		git("push", "--force", "origin", "release")

		// Reset develop to before the release commit. We're still on develop and
		// this commit doesn't touch the wrapper jar, so a hard reset is safe here.
		println("Resetting develop to pre-release state...")
		git("reset", "--hard", "HEAD~1")
		git("push", "--force", "origin", "develop")

		// Delete every tag for this version (exact + any +platform suffixes) from
		// remote then local. Remote delete is tried first so a partial failure
		// (network blip) doesn't leave us with a local tag that re-pushes later.
		val matchingTags = findReleaseTags()
		if (matchingTags.isEmpty()) {
			println("No tags matching $tagName or $tagName+* found.")
		} else {
			matchingTags.forEach { tag ->
				println("Deleting tag $tag from remote and local...")
				gitSafe("push", "origin", "--delete", tag)
				gitSafe("tag", "-d", tag)
			}
		}

		// Delete the GitHub release(s) for this version if any exist. A draft
		// release has no git tag yet, so GET /releases/tags/{tag} returns 404 for
		// it — the draft is invisible to a by-tag lookup. List releases and match
		// on the tag_name field instead, which does see drafts.
		val ghToken = System.getenv("GH_TOKEN") ?: System.getenv("GITHUB_TOKEN")
		if (ghToken == null) {
			println("No GH_TOKEN or GITHUB_TOKEN found — skipping GitHub draft release deletion.")
		} else {
			val repoSlug = "Darkrock-Studios/hammer-editor"

			fun githubApi(method: String, path: String): Pair<Int, String> {
				val conn = java.net.URL("https://api.github.com/repos/$repoSlug/$path")
					.openConnection() as java.net.HttpURLConnection
				conn.requestMethod = method
				conn.setRequestProperty("Authorization", "Bearer $ghToken")
				conn.setRequestProperty("Accept", "application/vnd.github+json")
				conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
				val status = conn.responseCode
				val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
					?.bufferedReader()?.readText().orEmpty()
				return status to body
			}

			println("Looking up GitHub releases for $tagName...")
			val (listStatus, listBody) = githubApi("GET", "releases?per_page=100")
			if (listStatus != 200) {
				println("Warning: GitHub API returned HTTP $listStatus when listing releases.")
			} else {
				@Suppress("UNCHECKED_CAST")
				val releases =
					groovy.json.JsonSlurper().parseText(listBody) as List<Map<String, Any?>>
				val matches = releases.filter { release ->
					val name = release["tag_name"] as? String ?: return@filter false
					isPlatformReleaseTag(name, tagName)
				}
				if (matches.isEmpty()) {
					println("No GitHub release found for $tagName — nothing to delete.")
				} else {
					matches.forEach { release ->
						val name = release["tag_name"]
						val releaseId = (release["id"] as Number).toLong()
						if (release["draft"] != true) {
							println("GitHub release $name is not a draft — skipping deletion.")
						} else {
							println("Deleting GitHub draft release $name (id=$releaseId)...")
							val (deleteStatus, _) = githubApi("DELETE", "releases/$releaseId")
							if (deleteStatus == 204) {
								println("GitHub draft release $name deleted.")
							} else {
								println("Warning: Failed to delete GitHub release $name (HTTP $deleteStatus).")
							}
						}
					}
				}
			}
		}

		println("Done. $tagName has been fully reverted on local and remote.")
	}
}
