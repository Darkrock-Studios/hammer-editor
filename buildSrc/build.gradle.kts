plugins {
	`kotlin-dsl`
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(kotlin("stdlib"))
	//implementation(libs.markdown)
	implementation(libs.kotlinx.datetime)
	// for the prepareForRelease dialog.
	implementation(libs.flatlaf)
	testImplementation(kotlin("test"))
}