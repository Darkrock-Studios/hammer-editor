package com.darkrockstudios.apps.hammer.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClassDataArchiveTest {
	private val root = Files.createTempDirectory("cds").toFile()
	private val cds = File(root, "cache/cds")
	private val dir = File(cds, "4.0.0")
	private val archive = File(dir, "hammer.jsa")

	/** An install: its launcher, which names it, and its one jar. */
	private inner class Install(name: String) {
		val launcher = File(root, "$name/bin/hammer").apply { parentFile.mkdirs(); writeText("launcher") }
		val jar = File(root, "$name/lib/app.jar").apply { parentFile.mkdirs(); writeText("classes") }
	}

	private val opt = Install("opt")

	@AfterTest
	fun cleanUp() {
		root.deleteRecursively()
	}

	private fun launch(install: Install = opt, isCli: Boolean = false, dir: File = this.dir) {
		prepareClassDataArchive(isCli, dir, install.launcher.path, install.jar.path)
		if (!isCli) adoptClassDataArchive(dir, install.launcher.path, install.jar.path)
	}

	/** What the JVM does at exit when it found no archive at start. */
	private fun exit(startedWithArchive: Boolean) {
		if (!startedWithArchive) archive.writeText("archive")
	}

	@Test
	fun `a launch makes the archive directory`() {
		launch(isCli = true)
		assertTrue(dir.isDirectory)
	}

	@Test
	fun `the window keeps an archive it wrote`() {
		launch()
		exit(startedWithArchive = false)
		launch()
		assertTrue(archive.exists())
	}

	@Test
	fun `the window deletes an archive the CLI wrote`() {
		launch(isCli = true)
		exit(startedWithArchive = false)
		launch()
		assertFalse(archive.exists())
	}

	@Test
	fun `a CLI run leaves the window's archive alone`() {
		launch()
		exit(startedWithArchive = false)
		launch(isCli = true)
		launch()
		assertTrue(archive.exists())
	}

	@Test
	fun `a CLI run still going when the window starts keeps its claim`() {
		launch(isCli = true)
		launch()
		exit(startedWithArchive = false)
		launch()
		assertFalse(archive.exists())
	}

	@Test
	fun `a launch handing off to a running window touches nothing`() {
		launch()
		exit(startedWithArchive = false)
		opt.jar.writeText("other classes")
		prepareClassDataArchive(isCli = false, dir, opt.launcher.path, opt.jar.path)
		assertTrue(archive.exists())
	}

	@Test
	fun `the window deletes an archive whose jars changed`() {
		launch()
		exit(startedWithArchive = false)
		opt.jar.writeText("other classes")
		launch()
		assertFalse(archive.exists())
	}

	@Test
	fun `a moved install keeps its archive`() {
		launch()
		exit(startedWithArchive = false)
		val moved = Install("moved")
		moved.jar.setLastModified(opt.jar.lastModified())
		opt.launcher.parentFile.parentFile.deleteRecursively()
		launch(moved)
		assertTrue(archive.exists())
	}

	@Test
	fun `another install of the version leaves the archive alone`() {
		launch()
		exit(startedWithArchive = false)
		val other = Install("other")
		other.jar.writeText("other classes")
		launch(other)
		launch(other)
		assertTrue(archive.exists())
	}

	@Test
	fun `the window drops its own install's other versions`() {
		launch(dir = File(cds, "3.12.0"))
		File(cds, "3.12.0/hammer.jsa").writeText("old")
		File(cds, "3.11.0").mkdirs()
		launch()
		assertEquals(listOf("4.0.0"), cds.list()?.toList())
	}

	@Test
	fun `the window keeps another install's version`() {
		launch(Install("beta"), dir = File(cds, "4.1.0"))
		File(cds, "4.1.0/hammer.jsa").writeText("beta")
		launch()
		assertEquals(setOf("4.0.0", "4.1.0"), cds.list()?.toSet())
	}

	@Test
	fun `the window drops the version of an install that is gone`() {
		val beta = Install("beta")
		launch(beta, dir = File(cds, "4.1.0"))
		beta.launcher.delete()
		launch()
		assertEquals(listOf("4.0.0"), cds.list()?.toList())
	}
}
