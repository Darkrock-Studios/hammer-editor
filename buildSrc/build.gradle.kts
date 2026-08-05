plugins {
	`kotlin-dsl`
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(kotlin("stdlib"))
	// for the prepareForRelease dialog.
	implementation(libs.flatlaf)
	testImplementation(kotlin("test"))
}