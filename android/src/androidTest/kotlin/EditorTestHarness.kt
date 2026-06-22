import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.darkrockstudios.apps.hammer.android.ProjectRootActivity
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import org.koin.java.KoinJavaComponent.getKoin

/**
 * Shared helpers for editor instrumented tests: seed a project through the real Koin graph,
 * launch straight into [ProjectRootActivity] (no click-through), navigate, and tear down
 * without racing the project-scope buffer flush.
 */
object EditorTestHarness {

	fun repository(): ProjectsRepository = getKoin().get()

	private fun context(): Context =
		InstrumentationRegistry.getInstrumentation().targetContext

	/** Create a uniquely-named project so re-runs don't collide; returns its def. */
	fun seedProject(baseName: String): ProjectDef {
		val result = repository().createProject("$baseName ${System.currentTimeMillis()}")
		check(result is ClientResult.Success) { "Failed to seed project: $result" }
		return result.data
	}

	fun launchEditor(projectDef: ProjectDef): ActivityScenario<ProjectRootActivity> =
		ActivityScenario.launch(ProjectRootActivity.createIntent(context(), projectDef))

	/** Finish + idle before deleting: the project-scope close flushes scene buffers and racing it crashes. */
	fun teardown(scenario: ActivityScenario<ProjectRootActivity>, projectDef: ProjectDef) {
		scenario.onActivity { it.finish() }
		InstrumentationRegistry.getInstrumentation().waitForIdleSync()
		scenario.close()
		repository().deleteProject(projectDef)
	}
}

/** Matches nodes whose testTag starts with [prefix] - for list items keyed by an unknown id. */
fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("testTag starts with '$prefix'") { node ->
	node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
}

/** Concatenated text of the first node with [tag], or empty if it isn't present. */
fun ComposeTestRule.textOf(tag: String): String =
	onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()
		?.config?.getOrNull(SemanticsProperties.Text)
		?.joinToString("") { it.text }
		.orEmpty()

/** Wait for a nav destination tag to render, then click it. */
fun ComposeTestRule.navigateTo(navTag: String) {
	waitUntil(timeoutMillis = 10_000) {
		onAllNodesWithTag(navTag).fetchSemanticsNodes().isNotEmpty()
	}
	onNodeWithTag(navTag).performClick()
}

/**
 * Type into the tagged markdown/scene editor and wait for the edit to land.
 *
 * The editor consumes hardware key events rather than exposing Compose SetText semantics, so
 * performTextInput can't drive it. Focus and the async edit flow can each still be unready when
 * the keystrokes fire, silently dropping them; re-focus and re-inject until [propagated] confirms
 * the change rather than trusting a single injection.
 */
fun ComposeTestRule.typeIntoEditor(
	tag: String,
	text: String,
	timeoutMillis: Long = 10_000L,
	propagated: () -> Boolean,
) {
	val instrumentation = InstrumentationRegistry.getInstrumentation()
	val deadline = SystemClock.uptimeMillis() + timeoutMillis
	while (true) {
		onNodeWithTag(tag).performClick()
		waitForIdle()
		instrumentation.sendStringSync(text)
		waitForIdle()
		try {
			waitUntil(timeoutMillis = 1_000L) { propagated() }
			return
		} catch (e: ComposeTimeoutException) {
			if (SystemClock.uptimeMillis() >= deadline) {
				throw AssertionError("Editor input for '$tag' never propagated within ${timeoutMillis}ms", e)
			}
		}
	}
}
