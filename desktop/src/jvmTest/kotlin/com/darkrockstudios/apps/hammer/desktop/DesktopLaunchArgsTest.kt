package com.darkrockstudios.apps.hammer.desktop

import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectDeepLink
import com.github.ajalt.clikt.core.CliktError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DesktopLaunchArgsTest {

	@Test
	fun `empty args yield defaults`() {
		val args = parseDesktopLaunchArgs(emptyArray())
		assertEquals(false, args.devMode)
		assertNull(args.projectName)
		assertNull(args.deepLink)
	}

	@Test
	fun `dev flag sets devMode`() {
		val args = parseDesktopLaunchArgs(arrayOf("--dev"))
		assertEquals(true, args.devMode)
	}

	@Test
	fun `project name is captured`() {
		val args = parseDesktopLaunchArgs(arrayOf("--project", "My Novel"))
		assertEquals("My Novel", args.projectName)
		assertNull(args.deepLink)
	}

	@Test
	fun `scene target maps to ProjectDeepLink Scene`() {
		val args = parseDesktopLaunchArgs(arrayOf("--project", "Novel", "--scene", "42"))
		assertEquals(ProjectDeepLink.Scene(42), args.deepLink)
	}

	@Test
	fun `note target maps to ProjectDeepLink Note`() {
		val args = parseDesktopLaunchArgs(arrayOf("--project", "Novel", "--note", "7"))
		assertEquals(ProjectDeepLink.Note(7), args.deepLink)
	}

	@Test
	fun `entry target maps to ProjectDeepLink EncyclopediaEntry`() {
		val args = parseDesktopLaunchArgs(arrayOf("--project", "Novel", "--entry", "13"))
		assertEquals(ProjectDeepLink.EncyclopediaEntry(13), args.deepLink)
	}

	@Test
	fun `timeline-event target maps to ProjectDeepLink TimelineEvent`() {
		val args = parseDesktopLaunchArgs(arrayOf("--project", "Novel", "--timeline-event", "99"))
		assertEquals(ProjectDeepLink.TimelineEvent(99), args.deepLink)
	}

	@Test
	fun `multiple targets are rejected`() {
		assertThrows<CliktError> {
			parseDesktopLaunchArgs(arrayOf("--project", "Novel", "--scene", "1", "--note", "2"))
		}
	}

	@Test
	fun `target without project is rejected`() {
		assertThrows<CliktError> {
			parseDesktopLaunchArgs(arrayOf("--scene", "1"))
		}
	}
}
