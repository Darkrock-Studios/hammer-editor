package architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

private val rootDir: File by lazy {
	val path = System.getProperty("hammer.rootDir")
		?: error("hammer.rootDir is not set, run these tests through Gradle")
	File(path)
}

/** Scope over source directories given relative to the repository root. */
fun scopeOf(vararg relativePaths: String): KoScope {
	return relativePaths
		.map { File(rootDir, it) }
		.onEach { assertTrue(it.isDirectory, "Expected a source directory at ${it.absolutePath}") }
		.map { Konsist.scopeFromExternalDirectory(it.absolutePath) }
		.reduce { acc, scope -> acc + scope }
}

fun clientScope(): KoScope = scopeOf(
	"common/src/commonMain",
	"common/src/androidMain",
	"common/src/desktopMain",
	"common/src/iosMain",
	"common/src/jvmMain",
)

fun serverScope(): KoScope = scopeOf("server/src/main")

/** A dependency one class declares on another. */
data class Dependency(val owner: String, val target: String, val lazy: Boolean) {
	override fun toString() = "$owner -> $target"
}

private val typeName = Regex("""\b[A-Z]\w*""")
private val lazyWrapper = Regex("""^(Lazy|Provider)<|^\(\)\s*->""")
private val koinLookup = Regex("""\b(?:get|getOrNull|\w*[iI]nject\w*)<([^()]+?)>\s*\(""")

/**
 * Every type each class in the scope takes as a constructor parameter, holds as a
 * property, or resolves from Koin in its body (`get<T>()`, `inject<T>()`).
 */
fun KoScope.dependencies(): List<Dependency> = classes(includeNested = true).flatMap { cls ->
	val constructorTypes = cls.constructors
		.flatMap { it.parameters }
		.map { it.type.text to lazyWrapper.containsMatchIn(it.type.text) }

	val propertyTypes = cls.properties(includeNested = false).mapNotNull { prop ->
		val declared = prop.type?.text ?: return@mapNotNull null
		declared to (prop.hasDelegate() || lazyWrapper.containsMatchIn(declared))
	}

	val lookupTypes = koinLookup.findAll(cls.text).map { it.groupValues[1] to true }

	(constructorTypes + propertyTypes + lookupTypes)
		.flatMap { (text, lazy) -> typeName.findAll(text).map { Dependency(cls.name, it.value, lazy) } }
		.filter { it.target != cls.name }
		.distinct()
}

/** Every cycle in the graph formed by [edges], each written as `A -> B -> A` starting at its first name alphabetically. */
fun findCycles(edges: List<Dependency>): Set<String> {
	val graph = edges.groupBy({ it.owner }, { it.target })
	val cycles = sortedSetOf<String>()

	fun visit(node: String, path: List<String>) {
		val start = path.indexOf(node)
		if (start >= 0) {
			val cycle = path.subList(start, path.size)
			val first = cycle.indexOf(cycle.min())
			val rotated = cycle.drop(first) + cycle.take(first)
			cycles += (rotated + rotated.first()).joinToString(" -> ")
			return
		}
		graph[node].orEmpty().forEach { visit(it, path + node) }
	}

	graph.keys.forEach { visit(it, emptyList()) }
	return cycles
}

/**
 * Fails on any violation missing from [baseline], and on any [baseline] entry that is
 * no longer a violation, so the baseline can only shrink.
 */
fun assertNoViolations(rule: String, violations: List<Dependency>, baseline: Set<String> = emptySet()) =
	assertNoViolations(rule, violations.map { it.toString() }.toSet(), baseline)

fun assertNoViolations(rule: String, violations: Set<String>, baseline: Set<String> = emptySet()) {
	val found = violations.toSortedSet()
	val new = found - baseline
	val stale = baseline - found

	val message = buildString {
		if (new.isNotEmpty()) {
			appendLine("$rule (docs/ARCHITECTURE.md). New violations:")
			new.forEach { appendLine("  $it") }
		}
		if (stale.isNotEmpty()) {
			appendLine("These baseline entries are no longer violations, remove them:")
			stale.forEach { appendLine("  $it") }
		}
	}
	if (message.isNotEmpty()) fail(message)
}
