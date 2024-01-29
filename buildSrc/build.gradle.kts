plugins {
	`kotlin-dsl`
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(kotlin("stdlib"))
	//implementation(libs.markdown)
	implementation(libs.datetime)
	testImplementation(kotlin("test"))
}