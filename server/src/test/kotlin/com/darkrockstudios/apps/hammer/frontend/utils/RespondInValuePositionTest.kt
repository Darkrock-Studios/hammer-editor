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
 * Ktor's `respond*` functions are tail-call suspend functions that return the send pipeline's final
 * subject rather than Unit. Before Kotlin 2.4.20 (KT-87499) a respond in *value position*, the last
 * expression of an exhaustive `when` with no `else` inside a route handler, made the compiler cast
 * that subject to Unit, and the handler died with a ClassCastException once the pipeline suspended.
 *
 * The shape is invisible in source review, so this scans the compiled route handlers for the
 * coercion the compiler used to emit (`throwOnFailure` on the resumed value, then `checkcast
 * kotlin/Unit`). [valuePositionFixtureRoutes] compiles the shape so the second test can pin the fix:
 * if a Kotlin upgrade brings the cast back, it fails there before any real handler does.
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
	fun `a respond result in value position is discarded, not cast to Unit`() {
		assertTrue(testClasses.exists(), "Compiled test classes not found at $testClasses")

		val fixture = routeHandlers(testClasses).filter { it.name.contains("valuePositionFixtureRoutes") }
		assertTrue(
			fixture.isNotEmpty(),
			"The fixture route handler was not found. The handler filter has probably stopped matching."
		)

		assertEquals(
			emptyList(), fixture.filter { it.coercesResumedValueToUnit() }.map { it.name },
			"The compiler casts a discarded respond result to Unit again (KT-87499), so every exhaustive " +
				"`when` that responds from value position is unsafe. Re-derive the pattern from `javap -c`."
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
 * An exhaustive `when` with no `else` whose branches each end in a respond, the shape KT-87499
 * miscompiled. Never registered on a real route; it exists to be compiled.
 */
internal fun Route.valuePositionFixtureRoutes() {
	post("/fixture") {
		when (pickOutcome()) {
			FixtureOutcome.Saved -> respondToast("saved")
			FixtureOutcome.Rejected -> respondToast("rejected")
		}
	}
}
