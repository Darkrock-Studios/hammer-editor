package com.darkrockstudios.build

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

private const val AOT_CACHE_FILE = "hammer.aot"

// The adapter region holds machine code for the training CPU, and JDK 25 does not check CPU
// features before running it: a cache trained on a wide CPU dies with SIGILL on a narrower one.
private val AOT_PORTABILITY_ARGS = listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:-AOTAdapterCaching")

/**
 * Trains a JDK 25+ AOT cache against a jpackage app image and points its launchers at it.
 *
 * Both steps run through the app's own launcher, so the cache is built against the bundled
 * runtime image and the launcher's classpath, which is what it is validated against at startup.
 * The two-step record/create workflow is used because the one-step `AOTCacheOutput` forks a
 * `bin/java` that a jpackage runtime does not have.
 */
fun trainAotCache(appImage: File, logger: Logger, timeoutSeconds: Long = 180) {
	val appDir = listOf("Contents/app", "app", "lib/app").map(appImage::resolve).firstOrNull { it.isDirectory }
		?: throw GradleException("No app directory in $appImage")
	val launcher = listOf("Contents/MacOS/hammer", "hammer.exe", "bin/hammer").map(appImage::resolve)
		.firstOrNull { it.isFile } ?: throw GradleException("No launcher in $appImage")
	val cfgFiles = appDir.listFiles { f -> f.extension == "cfg" }.orEmpty()
	if (cfgFiles.any { it.readText().contains("AOTCache") }) return
	val runtimeVersion = runtimeMajorVersion(appImage)
	if (runtimeVersion == null || runtimeVersion < 25) {
		logger.warn("[aot] Skipped: the bundled runtime is JDK $runtimeVersion; an AOT cache needs 25+")
		return
	}

	val cache = appDir.resolve(AOT_CACHE_FILE)
	val work = Files.createTempDirectory("hammer-aot").toFile()
	val xvfb = startXvfbIfHeadless(logger)
	val display = if (xvfb != null) XVFB_DISPLAY else null
	try {
		val config = work.resolve("hammer.aotconf")
		val home = work.resolve("home").apply { mkdirs() }

		logger.lifecycle("[aot] Training run")
		runLauncher(
			launcher, home, display, logger, timeoutSeconds,
			AOT_PORTABILITY_ARGS + listOf(
				"-XX:AOTMode=record",
				"-XX:AOTConfiguration=${config.absolutePath}",
				"-Dnucleus.aot.mode=training",
				"-Dnucleus.tao.fatalErrorDialog=false",
			),
		)
		if (!config.isFile) throw GradleException("AOT training wrote no configuration")

		logger.lifecycle("[aot] Assembling cache")
		runLauncher(
			launcher, home, display, logger, timeoutSeconds,
			AOT_PORTABILITY_ARGS + listOf(
				"-XX:AOTMode=create",
				"-XX:AOTConfiguration=${config.absolutePath}",
				"-XX:AOTCache=${cache.absolutePath}",
			),
		)
		if (!cache.isFile) throw GradleException("AOT assembly wrote no cache")
	} finally {
		xvfb?.destroyForcibly()
		work.deleteRecursively()
	}

	// A cache that fails validation (any jar's mtime changed) is skipped with a warning the JVM prints to
	// stdout, where the CLI writes its JSON; send JVM logging to stderr instead.
	val runtimeArgs = AOT_PORTABILITY_ARGS + listOf(
		"-XX:AOTCache=\$APPDIR/$AOT_CACHE_FILE",
		"-Xlog:disable",
		"-Xlog:all=warning:stderr",
	)
	val options = runtimeArgs
		.joinToString("\n", prefix = "[JavaOptions]\n") { "java-options=$it" }
	cfgFiles.forEach { cfg -> cfg.writeText(cfg.readText().replace("[JavaOptions]", options)) }
	logger.lifecycle("[aot] ${cache.name}: ${cache.length() / 1024 / 1024} MB")
}

private fun runtimeMajorVersion(appImage: File): Int? =
	listOf("Contents/runtime/Contents/Home", "runtime", "lib/runtime")
		.map { appImage.resolve(it).resolve("release") }
		.firstOrNull { it.isFile }
		?.readLines()
		?.firstOrNull { it.startsWith("JAVA_VERSION=") }
		?.substringAfter('"')?.substringBefore('.')?.substringBefore('"')
		?.toIntOrNull()

private const val XVFB_DISPLAY = ":99"

/** Training opens real windows, so a Linux host with no display (CI) gets a virtual one. */
private fun startXvfbIfHeadless(logger: Logger): Process? {
	val linux = System.getProperty("os.name").lowercase().contains("linux")
	if (!linux || !System.getenv("DISPLAY").isNullOrEmpty() || !System.getenv("WAYLAND_DISPLAY").isNullOrEmpty()) return null
	val process = try {
		ProcessBuilder("Xvfb", XVFB_DISPLAY, "-screen", "0", "1280x1024x24")
			.redirectErrorStream(true)
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.start()
	} catch (e: java.io.IOException) {
		throw GradleException("AOT training needs a display; install Xvfb or pass -Paot=false", e)
	}
	Thread.sleep(1000)
	logger.lifecycle("[aot] Started Xvfb on $XVFB_DISPLAY")
	return process
}

/** Runs the app against a throwaway home so training never touches real projects, settings, or a running instance. */
private fun runLauncher(
	launcher: File,
	home: File,
	xvfbDisplay: String?,
	logger: Logger,
	timeoutSeconds: Long,
	jvmOptions: List<String>,
) {
	val log = home.resolveSibling("launcher.log")
	val process = ProcessBuilder(launcher.absolutePath, "--dev")
		.directory(home)
		.redirectErrorStream(true)
		.redirectOutput(log)
		.apply {
			environment().apply {
				// The JVM splits this on spaces unless an option is quoted.
				put("JAVA_TOOL_OPTIONS", (jvmOptions + "-Duser.home=${home.absolutePath}").joinToString(" ") { "\"$it\"" })
				put("HOME", home.absolutePath)
				put("XDG_CONFIG_HOME", home.resolve(".config").absolutePath)
				put("XDG_CACHE_HOME", home.resolve(".cache").absolutePath)
				put("XDG_DATA_HOME", home.resolve(".local/share").absolutePath)
				put("APPDATA", home.resolve("AppData/Roaming").absolutePath)
				put("LOCALAPPDATA", home.resolve("AppData/Local").absolutePath)
				if (xvfbDisplay != null) {
					put("DISPLAY", xvfbDisplay)
					remove("WAYLAND_DISPLAY")
				}
			}
		}
		.start()
	val exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
	if (!exited) process.destroyForcibly().waitFor()
	val output = log.readText()
	if (!exited || process.exitValue() != 0) {
		logger.lifecycle(output.takeLast(4000))
		throw GradleException(if (exited) "AOT step exited with ${process.exitValue()}" else "AOT step timed out")
	}
	logger.info(output)
}
