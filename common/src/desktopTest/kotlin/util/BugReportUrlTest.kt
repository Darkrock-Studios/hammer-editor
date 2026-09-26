package util

import com.darkrockstudios.apps.hammer.common.util.BUG_REPORT_ENVIRONMENT_FIELD
import com.darkrockstudios.apps.hammer.common.util.BUG_REPORT_TEMPLATE
import com.darkrockstudios.apps.hammer.common.util.buildBugReportUrl
import io.ktor.http.Url
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BugReportUrlTest {

	@Test
	fun `the link opens the bug report form on the repository`() {
		val url = Url(buildBugReportUrl(environment = "anything", repoUrl = "https://github.com/owner/repo/"))

		assertEquals("github.com", url.host)
		assertEquals("/owner/repo/issues/new", url.encodedPath)
		assertEquals(BUG_REPORT_TEMPLATE, url.parameters["template"])
	}

	@Test
	fun `the environment arrives intact however it is punctuated`() {
		val banner = "Hammer v3.9.8 | channel: dev | OS: Windows 11 10.0 (amd64) | a&b=c #1 +2"

		val url = Url(buildBugReportUrl(environment = banner))

		assertEquals(banner, url.parameters[BUG_REPORT_ENVIRONMENT_FIELD])
	}

	@Test
	fun `the form the link names exists and has the field the link fills`() {
		val repoRoot = assertNotNull(
			generateSequence(File("").absoluteFile) { it.parentFile }
				.firstOrNull { File(it, ".github").isDirectory },
			"no .github directory above ${File("").absolutePath}",
		)
		val form = File(repoRoot, ".github/ISSUE_TEMPLATE/$BUG_REPORT_TEMPLATE")

		assertTrue(form.isFile, "missing issue form at $form")
		assertTrue(
			Regex("""^\s*id:\s*$BUG_REPORT_ENVIRONMENT_FIELD\s*$""", RegexOption.MULTILINE).containsMatchIn(form.readText()),
			"$form has no '$BUG_REPORT_ENVIRONMENT_FIELD' field for the link to fill",
		)
	}
}
