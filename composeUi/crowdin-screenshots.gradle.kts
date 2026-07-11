// Uploads tablet screen screenshots to Crowdin with exact, coordinate-accurate
// string tags. The tags come from the ScreenshotTagExtractorTest, which renders
// each screen and maps every text node back to its resource key; this task maps
// those keys to Crowdin string ids (identifier == our XML name) and posts the
// tags at pixel-accurate positions. No OCR.
//
//   ./gradlew :composeUi:uploadCrowdinScreenshots                  # dry run (default)
//   ./gradlew :composeUi:uploadCrowdinScreenshots -Pcrowdin.live=true
//
// Screenshots are matched to existing ones by name and replaced in place, so
// re-runs update rather than duplicate.
//
// Auth resolves in order: -Pcrowdin.projectId / -Pcrowdin.token, then the
// CROWDIN_PROJECT_ID / CROWDIN_PERSONAL_TOKEN env vars, then an interactive
// prompt (token hidden). Interactive prompting needs a real console — run with
// --console=plain --no-daemon if a prompt appears blank.

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

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
	description = "Upload tablet screenshots to Crowdin with exact string tags (dry run unless -Pcrowdin.live=true)."

	// The extractor test renders the screens and writes the tag artifacts.
	dependsOn("desktopTest")

	val crowdinDir = layout.buildDirectory.dir("crowdin")
	val live = providers.gradleProperty("crowdin.live").map { it == "true" }.orElse(false)
	val branchProp = providers.gradleProperty("crowdin.branch").orNull
	val projectIdProp = providers.gradleProperty("crowdin.projectId").orNull
	val tokenProp = providers.gradleProperty("crowdin.token").orNull

	doLast {
		val base = "https://api.crowdin.com/api/v2"
		val dir = crowdinDir.get().asFile
		val reports = dir.listFiles { f -> f.isFile && f.name.endsWith(".tags.json") }
			?.sortedBy { it.name }.orEmpty()
		require(reports.isNotEmpty()) {
			"No tag artifacts in ${dir.path}. Run with --rerun-tasks to regenerate them."
		}

		val projectId = projectIdProp
			?: System.getenv("CROWDIN_PROJECT_ID")
			?: promptFor("Crowdin project ID: ", hidden = false)
			?: throw GradleException("No Crowdin project ID provided.")
		val token = tokenProp
			?: System.getenv("CROWDIN_PERSONAL_TOKEN")
			?: promptFor("Crowdin personal token (hidden): ", hidden = true)
			?: throw GradleException("No Crowdin personal token provided.")

		val dryRun = !live.get()
		val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()

		fun send(method: String, url: String, jsonBody: String?, binary: ByteArray? = null, fileName: String? = null): Any? {
			var b = HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer $token")
			when {
				binary != null -> b = b.header("Crowdin-API-FileName", fileName)
					.header("Content-Type", "application/octet-stream")
					.method(method, HttpRequest.BodyPublishers.ofByteArray(binary))
				jsonBody != null -> b = b.header("Content-Type", "application/json")
					.method(method, HttpRequest.BodyPublishers.ofString(jsonBody))
				else -> b = b.method(method, HttpRequest.BodyPublishers.noBody())
			}
			val resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString())
			if (resp.statusCode() !in 200..299) {
				throw GradleException("$method $url -> ${resp.statusCode()}: ${resp.body()}")
			}
			return if (resp.body().isNullOrBlank()) null else JsonSlurper().parseText(resp.body())
		}

		@Suppress("UNCHECKED_CAST")
		fun dataOf(o: Any?): Map<String, Any?> = (o as Map<String, Any?>)["data"] as Map<String, Any?>

		// key (identifier) -> string id
		val idByIdentifier = HashMap<String, Long>()
		var offset = 0
		while (true) {
			val page = send("GET", "$base/projects/$projectId/strings?limit=500&offset=$offset", null) as Map<*, *>
			val items = page["data"] as List<*>
			for (item in items) {
				val s = (item as Map<*, *>)["data"] as Map<*, *>
				val ident = s["identifier"] as? String ?: continue
				idByIdentifier[ident] = (s["id"] as Number).toLong()
			}
			if (items.size < 500) break
			offset += 500
		}
		logger.lifecycle("Crowdin: ${idByIdentifier.size} source strings")

		// existing screenshots by name (for idempotent replace)
		val idByName = HashMap<String, Long>()
		val existing = send("GET", "$base/projects/$projectId/screenshots?limit=500", null) as Map<*, *>
		for (item in existing["data"] as List<*>) {
			val s = (item as Map<*, *>)["data"] as Map<*, *>
			idByName[s["name"] as String] = (s["id"] as Number).toLong()
		}

		logger.lifecycle(if (dryRun) "== DRY RUN (pass -Pcrowdin.live=true to upload) ==" else "== LIVE upload ==")
		var totalTags = 0
		var totalMissing = 0
		for (report in reports) {
			val parsed = JsonSlurper().parseText(report.readText()) as Map<*, *>
			val screen = parsed["screen"] as String
			val pngName = "$screen.png"
			val png = File(dir, pngName)
			val rawTags = parsed["tags"] as List<*>

			val tags = mutableListOf<Map<String, Any>>()
			var missing = 0
			for (t in rawTags) {
				val tm = t as Map<*, *>
				val key = tm["key"] as String
				val sid = idByIdentifier[key]
				val w = (tm["width"] as Number).toInt()
				val h = (tm["height"] as Number).toInt()
				val x = (tm["x"] as Number).toInt()
				val y = (tm["y"] as Number).toInt()
				if (sid == null) { missing++; continue }
				if (w <= 0 || h <= 0 || x < 0 || y < 0) continue
				tags.add(mapOf("stringId" to sid, "position" to mapOf("x" to x, "y" to y, "width" to w, "height" to h)))
			}
			totalTags += tags.size
			totalMissing += missing
			logger.lifecycle("  $pngName: ${tags.size} tags${if (missing > 0) " ($missing unmapped keys)" else ""}${if (pngName in idByName) " [replace]" else " [new]"}")

			if (dryRun) continue

			val storageId = (dataOf(send("POST", "$base/storages", null, png.readBytes(), pngName))["id"] as Number).toLong()
			val screenshotId = idByName[pngName]?.also {
				send("PUT", "$base/projects/$projectId/screenshots/$it", JsonOutput.toJson(mapOf("storageId" to storageId)))
			} ?: run {
				val body = mutableMapOf<String, Any>("storageId" to storageId, "name" to pngName, "autoTag" to false)
				if (branchProp != null) body["branchId"] = branchProp
				(dataOf(send("POST", "$base/projects/$projectId/screenshots", JsonOutput.toJson(body)))["id"] as Number).toLong()
			}
			send("PUT", "$base/projects/$projectId/screenshots/$screenshotId/tags", JsonOutput.toJson(tags))
		}

		logger.lifecycle("${if (dryRun) "Would tag" else "Tagged"} $totalTags strings across ${reports.size} screenshots" +
			if (totalMissing > 0) " ($totalMissing keys not found in project)" else "")
	}
}
