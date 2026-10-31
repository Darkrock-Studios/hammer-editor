package com.darkrockstudios.apps.hammer.base

import kotlin.test.Test
import kotlin.test.assertEquals

class StoryChaptersTest {

	private class Node(val name: String, val children: List<Node> = emptyList(), val scene: Boolean = false)

	private fun scene(name: String) = Node(name, scene = true)
	private fun group(name: String, vararg children: Node) = Node(name, children.toList())

	private fun chapters(vararg topLevel: Node) =
		storyChapters(topLevel.toList(), { it.children }, { it.scene })
			.map { it.top.name to it.scenes.map { scene -> scene.name } }

	@Test
	fun `a top-level group is one chapter of its scenes, nested groups flattened depth first`() {
		val result = chapters(
			group("Part One", scene("a"), group("Interlude", scene("b"), scene("c")), scene("d")),
		)

		assertEquals(listOf("Part One" to listOf("a", "b", "c", "d")), result)
	}

	@Test
	fun `a top-level scene is a chapter of its own`() {
		val result = chapters(scene("Prologue"), group("Part One", scene("a")))

		assertEquals(listOf("Prologue" to listOf("Prologue"), "Part One" to listOf("a")), result)
	}

	@Test
	fun `an empty group is a chapter with no scenes`() {
		assertEquals(listOf("Empty" to emptyList<String>()), chapters(group("Empty")))
	}
}
