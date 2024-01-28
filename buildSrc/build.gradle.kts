plugins {
	`kotlin-dsl`
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(kotlin("stdlib"))
	implementation(libs.markdown)
	testImplementation(kotlin("test"))
}