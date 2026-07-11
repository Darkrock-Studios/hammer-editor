// Renders Compose previews and uploads them to Crowdin with automatic string
// tagging. Auto-tag is a best-effort text match; mono labels and format
// strings (%1$s) usually still need a manual pass in the Crowdin editor.
//
//   ./gradlew :composeUi:uploadCrowdinScreenshots
//
// By default only the tablet-form screen previews (Screen*Tablet*.png) are
// uploaded.
//
// Auth resolves in order: -Pcrowdin.projectId / -Pcrowdin.token, then the
// CROWDIN_PROJECT_ID / CROWDIN_PERSONAL_TOKEN env vars, then an interactive
// prompt (token hidden). Interactive prompting needs a real console — run with
// --console=plain --no-daemon if a prompt appears blank.
//
// Options (Gradle properties):
//   -Pcrowdin.noRender               Skip the render step; upload existing PNGs.
//   -Pcrowdin.screenshots.pattern=X  Filename glob to override the default, e.g. 'Screen*'.
//   -Pcrowdin.branch=NAME            Attach screenshots to a Crowdin branch.

fun globToRegex(glob: String): Regex {
	val sb = StringBuilder()
	for (c in glob) when (c) {
		'*' -> sb.append(".*")
		'?' -> sb.append('.')
		'.', '(', ')', '+', '|', '^', '$', '@', '%', '\\', '{', '}', '[', ']' ->
			sb.append('\\').append(c)
		else -> sb.append(c)
	}
	return Regex(sb.toString(), RegexOption.IGNORE_CASE)
}

fun promptFor(message: String, hidden: Boolean): String? {
	val console = System.console()
	val value = if (console != null) {
		if (hidden) console.readPassword(message)?.concatToString()
		else console.readLine(message)
	} else {
		print(message)
		System.out.flush()
		readlnOrNull()
	}
	return value?.trim()?.takeIf { it.isNotEmpty() }
}

tasks.register("uploadCrowdinScreenshots") {
	group = "crowdin"
	description = "Render Compose previews and upload them to Crowdin with auto-tagging."

	if (!providers.gradleProperty("crowdin.noRender").isPresent) {
		dependsOn("composePreviewRender")
	}

	val rendersDir = layout.buildDirectory.dir("compose-previews/renders")
	val patternProp = providers.gradleProperty("crowdin.screenshots.pattern").orNull
	val branchProp = providers.gradleProperty("crowdin.branch").orNull
	val projectIdProp = providers.gradleProperty("crowdin.projectId").orNull
	val tokenProp = providers.gradleProperty("crowdin.token").orNull
	val repoRoot = rootProject.layout.projectDirectory.asFile
	val isWindows = System.getProperty("os.name").lowercase().contains("win")

	doLast {
		val dir = rendersDir.get().asFile
		require(dir.isDirectory) {
			"No renders at ${dir.path}. Run without -Pcrowdin.noRender first."
		}

		val glob = patternProp ?: "Screen*Tablet*.png"
		val regex = globToRegex(glob)
		val files = dir.listFiles { f ->
			f.isFile && f.name.endsWith(".png", ignoreCase = true) && regex.matches(f.name)
		}?.sortedBy { it.name }.orEmpty()
		require(files.isNotEmpty()) { "No PNGs in ${dir.path} matching '$glob'." }

		val projectId = projectIdProp
			?: System.getenv("CROWDIN_PROJECT_ID")
			?: promptFor("Crowdin project ID: ", hidden = false)
			?: throw GradleException("No Crowdin project ID provided.")
		val token = tokenProp
			?: System.getenv("CROWDIN_PERSONAL_TOKEN")
			?: promptFor("Crowdin personal token (hidden): ", hidden = true)
			?: throw GradleException("No Crowdin personal token provided.")

		val launcher = if (isWindows) listOf("cmd", "/c", "crowdin") else listOf("crowdin")

		logger.lifecycle("Uploading ${files.size} screenshot(s) to Crowdin with auto-tagging")
		var failed = 0
		for (f in files) {
			logger.lifecycle("--> ${f.name}")
			val command = launcher + listOf(
				"screenshot", "upload", f.absolutePath,
				"--name", f.name,
				"--auto-tag",
				"--update-strings",
			) + (branchProp?.let { listOf("--branch", it) } ?: emptyList())

			val process = ProcessBuilder(command)
				.directory(repoRoot)
				.inheritIO()
				.apply {
					environment()["CROWDIN_PROJECT_ID"] = projectId
					environment()["CROWDIN_PERSONAL_TOKEN"] = token
				}
				.start()
			if (process.waitFor() != 0) {
				failed++
				logger.error("    upload failed for ${f.name}")
			}
		}

		if (failed > 0) throw GradleException("$failed screenshot upload(s) failed.")
		logger.lifecycle("Done. Review tag placement in the Crowdin editor.")
	}
}
