import com.darkrockstudios.build.registerLinuxDistributionTasks
import com.darkrockstudios.build.registerPrepareForReleaseTask
import com.darkrockstudios.build.registerPublishTasks
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
	alias(libs.plugins.compose.report.generator) apply false
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
		total {

		}
	}
}

registerPrepareForReleaseTask()
registerPublishTasks()
registerLinuxDistributionTasks(libs.versions.app.get())