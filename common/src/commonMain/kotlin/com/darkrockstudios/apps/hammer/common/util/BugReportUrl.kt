package com.darkrockstudios.apps.hammer.common.util

import com.darkrockstudios.apps.hammer.base.GITHUB_URL
import io.ktor.http.encodeURLParameter

/** Cap on how much of a crash trace we inline in the URL, keeping the whole link a sane length. */
private const val MAX_CRASH_EMBED_CHARS = 4000

/**
 * Builds a GitHub "new issue" URL with a pre-filled title, body and label.
 *
 * GitHub supports pre-filling an issue through query parameters, but it has no way to
 * attach files this way (there is no public attachment API at all). So the body includes
 * the environment details we already know, the most recent crash trace when there is one,
 * and a reminder to attach the log archive the app exports for them.
 *
 * @param appVersion the running app version, e.g. `v1.2.3`
 * @param platformName the coarse platform name, e.g. `Desktop`, `Android`, `iOS`
 * @param platformInfo richer OS/device details (see `platformStartupInfo()`)
 * @param crashContent the most recent crash dump to inline, or null when there is none
 * @param repoUrl the GitHub repository URL to file the issue against
 */
fun buildBugReportUrl(
	appVersion: String,
	platformName: String,
	platformInfo: String,
	crashContent: String? = null,
	repoUrl: String = GITHUB_URL,
): String {
	val body = buildString {
		appendLine("## What happened?")
		appendLine()
		appendLine("<!-- Describe the bug. What did you expect to happen, and what happened instead? -->")
		appendLine()
		appendLine("## Steps to reproduce")
		appendLine()
		appendLine("1. ")
		appendLine("2. ")
		appendLine("3. ")
		appendLine()

		if (!crashContent.isNullOrBlank()) {
			val trimmed = crashContent.trim()
			val truncated = trimmed.length > MAX_CRASH_EMBED_CHARS
			val excerpt = if (truncated) trimmed.take(MAX_CRASH_EMBED_CHARS) else trimmed

			appendLine("## Most recent crash")
			appendLine()
			appendLine("<details><summary>Stack trace</summary>")
			appendLine()
			appendLine("```")
			appendLine(excerpt)
			if (truncated) {
				appendLine("… (truncated — the full crash was copied to your clipboard, paste it here)")
			}
			appendLine("```")
			appendLine()
			appendLine("</details>")
			appendLine()
		}

		appendLine("## Logs")
		appendLine()
		appendLine("📎 Please attach the log archive the app exported for you (drag the `.zip` into this box).")
		appendLine()
		appendLine("## Environment")
		appendLine()
		appendLine("- App version: $appVersion")
		appendLine("- Platform: $platformName")
		appendLine("- $platformInfo")
	}

	val base = repoUrl.trimEnd('/') + "/issues/new"
	val title = "[Bug] "
	return base +
		"?title=" + title.encodeURLParameter() +
		"&labels=" + "bug".encodeURLParameter() +
		"&body=" + body.encodeURLParameter()
}
