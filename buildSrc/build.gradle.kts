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
	testImplementation(kotlin("test"))
}