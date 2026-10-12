plugins {
	alias(libs.plugins.kotlin.jvm)
}

group = "com.darkrockstudios.apps.hammer"
version = libs.versions.app.get()

kotlin {
	jvmToolchain(libs.versions.jvm.get().toInt())
}

dependencies {
	testImplementation(libs.konsist)
	testImplementation(kotlin("test"))
	testImplementation(libs.bundles.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)
}

// Konsist parses these sources from disk rather than depending on the modules,
// so they have to be declared as inputs for the test task to rerun when they change.
val scannedSources = listOf("common/src", "server/src/main")

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
	systemProperty("hammer.rootDir", rootProject.projectDir.absolutePath)
	scannedSources.forEach { path ->
		inputs.dir(rootProject.file(path))
			.withPropertyName("scanned:$path")
			.withPathSensitivity(PathSensitivity.RELATIVE)
	}
}
