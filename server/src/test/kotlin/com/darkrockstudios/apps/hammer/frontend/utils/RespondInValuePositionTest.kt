package com.darkrockstudios.apps.hammer.frontend.utils

import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ktor's `respond*` functions are tail-call suspend functions: they return the send pipeline's final
 * subject (a `CompressedReadChannelResponse` once Compression has run) rather than Unit. A respond in
 * *value position* — the last expression of an exhaustive `when` with no `else` inside a route
 * handler — makes the compiler cast that subject to Unit, and the handler dies with a
 * ClassCastException once the pipeline suspends.
 *
 * The shape is invisible in source review: adding an `else`, or wrapping in `if/else`, is safe, while
 * a trailing `return@post` is not. So this scans the compiled route handlers instead, looking for the
 * coercion the compiler emits (`throwOnFailure` on the resumed value, then `checkcast kotlin/Unit`).
 *
 * [unsafeFixtureRoutes] is a positive control. If a Kotlin upgrade changes this codegen, the detector
 * stops firing on the fixture and this test fails rather than silently passing forever.
 */
class RespondInValuePositionTest {

	private val mainClasses = Path.of("build/classes/kotlin/main")
	private val testClasses = Path.of("build/classes/kotlin/test")

	private companion object {
		const val MIN_EXPECTED_HANDLERS = 40
	}

	@Test
	fun `no route handler coerces a respond result to Unit`() {
		assertTrue(mainClasses.exists(), "Compiled main classes not found at $mainClasses")

		val handlers = routeHandlers(mainClasses)

		// A floor on the denominator: if a Ktor change breaks the handler filter, the scan would find
		// nothing to check and pass regardless of what the routes do.
		assertTrue(
			handlers.size >= MIN_EXPECTED_HANDLERS,
			"Only found ${handlers.size} route handlers to scan, expected at least " +
				"$MIN_EXPECTED_HANDLERS. The handler filter has probably stopped matching."
		)

		val offenders = handlers.filter { it.coercesResumedValueToUnit() }.map { it.name }.sorted()

		assertEquals(
			emptyList(), offenders,
			"These route handlers respond from value position (exhaustive `when` with no `else`). " +
				"Have the `when` yield a value and respond once after it."
		)
	}

	@Test
	fun `the detector still recognises the unsafe shape`() {
		assertTrue(testClasses.exists(), "Compiled test classes not found at $testClasses")

		val detected = routeHandlers(testClasses)
			.filter { it.coercesResumedValueToUnit() }
			.map { it.name }

		assertTrue(
			detected.any { it.contains("unsafeFixtureRoutes") },
			"The detector no longer flags the known-unsafe fixture, so it can no longer protect the " +
				"main source set. Kotlin's codegen for this shape has likely changed — re-derive the " +
				"pattern from `javap -c` before trusting this test again. Flagged: $detected"
		)
	}

	@OptIn(kotlin.io.path.ExperimentalPathApi::class)
	private fun routeHandlers(root: Path): List<ClassNode> = root.walk()
		.filter { it.isRegularFile() && it.toString().endsWith(".class") }
		.map { ClassNode().also { node -> ClassReader(it.readBytes()).accept(node, 0) } }
		.filter { it.isRouteHandler() }
		.toList()

	/** A `suspend RoutingContext.() -> Unit` lambda, i.e. the body of a `get`/`post`/... route. */
	private fun ClassNode.isRouteHandler(): Boolean =
		superName == "kotlin/coroutines/jvm/internal/SuspendLambda" &&
			signature?.contains("io/ktor/server/routing/RoutingContext") == true

	/**
	 * Looks for the resumption sequence the compiler emits when a suspend call's result is used as a
	 * Unit-typed value: `ResultKt.throwOnFailure(result)` followed by a `checkcast` of that same
	 * result. A `checkcast kotlin/Unit` on a restored local (`getfield L$n`) is unrelated and benign.
	 */
	private fun ClassNode.coercesResumedValueToUnit(): Boolean = methods.any { method ->
		val instructions = method.instructions.filter { it.opcode >= 0 }
		instructions.withIndex().any { (index, insn) ->
			insn is MethodInsnNode &&
				insn.owner == "kotlin/ResultKt" &&
				insn.name == "throwOnFailure" &&
				instructions.drop(index + 1).take(2).any { next ->
					next is TypeInsnNode && next.opcode == Opcodes.CHECKCAST && next.desc == "kotlin/Unit"
				}
		}
	}
}

private enum class FixtureOutcome { Saved, Rejected }

private fun pickOutcome() = FixtureOutcome.Saved

/**
 * Positive control for [RespondInValuePositionTest]: an exhaustive `when` with no `else` whose
 * branches each end in a respond. Never registered on a real route — it exists to be compiled.
 */
internal fun Route.unsafeFixtureRoutes() {
	post("/fixture") {
		when (pickOutcome()) {
			FixtureOutcome.Saved -> respondToast("saved")
			FixtureOutcome.Rejected -> respondToast("rejected")
		}
	}
}
