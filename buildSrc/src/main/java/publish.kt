package com.darkrockstudios.build

import org.gradle.api.Project

fun publishFdroid(project: Project) {
	val libs = project.extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java).named("libs")
	val semvarStr = libs.findVersion("app").get().toString()
	val curSemVar = parseSemVar(semvarStr)
	val versionCode = curSemVar.createVersionCode(true, 0)
	val tag = "fdroid-${versionCode}"

	project.exec {
		commandLine = listOf(
			"git",
			"config",
			"--global",
			"user.email",
			"github-actions[bot]@users.noreply.github.com"
		)
	}
	project.exec {
		commandLine = listOf("git", "config", "--global", "user.name", "github-actions[bot]")
	}

	project.exec { commandLine = listOf("git", "fetch", "origin", "release") }
	project.exec { commandLine = listOf("git", "checkout", "release") }
	project.exec {
		commandLine = listOf(
			"git",
			"tag",
			"-a",
			tag,
			"-m",
			"FDroid release tag for $semvarStr"
		)
	}
	project.exec { commandLine = listOf("git", "push", "origin", "tag", tag) }
}

fun publishFlathub(project: Project) {
	val libs = project.extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java).named("libs")
	val semvarStr = libs.findVersion("app").get().toString()
	val tag = "flathub-v${semvarStr}"

	project.exec {
		commandLine = listOf(
			"git",
			"config",
			"--global",
			"user.email",
			"github-actions[bot]@users.noreply.github.com"
		)
	}
	project.exec {
		commandLine = listOf("git", "config", "--global", "user.name", "github-actions[bot]")
	}

	project.exec { commandLine = listOf("git", "fetch", "origin", "release") }
	project.exec { commandLine = listOf("git", "checkout", "release") }
	project.exec {
		commandLine = listOf(
			"git",
			"tag",
			"-a",
			tag,
			"-m",
			"Flathub release tag for $semvarStr"
		)
	}
	project.exec { commandLine = listOf("git", "push", "origin", "tag", tag) }
}

/**
 * Registers the publish tasks for F-Droid and Flathub.
 */
fun Project.registerPublishTasks() {
	tasks.register("publishFdroid") {
		doLast {
			publishFdroid(project)
		}
	}

	tasks.register("publishFlathub") {
		doLast {
			publishFlathub(project)
		}
	}
}
