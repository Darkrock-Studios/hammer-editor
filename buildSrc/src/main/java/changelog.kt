package com.darkrockstudios.build

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.concurrent.CountDownLatch
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

fun writeSemvar(oldSemVar: String, newSemVar: SemVar, versionFile: File) {
	val versions = versionFile.readText()
	val updated = versions.replace("app = \"$oldSemVar\"", "app = \"$newSemVar\"")
	versionFile.writeText(updated)
}

fun writeChangelogMarkdown(releaseInfo: ReleaseInfo, changelogFile: File) {
	val currentChangelog = changelogFile.readText()
	val withoutHeader = currentChangelog.substring(currentChangelog.indexOf('\n') + 1)

	val now = Clock.System.now()
	val date: LocalDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
	val headerDate = "${date.year}-${date.monthNumber}-${date.dayOfMonth}"
	val newEntry = "# [${releaseInfo.semVar}] $headerDate\n\n" + releaseInfo.changeLog + "\n\n"

	val newChangeLog = "# Changelog\n\n" + newEntry + withoutHeader

	changelogFile.writeText(newChangeLog)
	println("CHANGELOG.md written")
}

data class ReleaseInfo(
	val semVar: SemVar,
	val changeLog: String,
)

class OnChangeListener(
	val onChange: (e: DocumentEvent?) -> Unit
) : DocumentListener {
	override fun insertUpdate(e: DocumentEvent?) = onChange(e)
	override fun removeUpdate(e: DocumentEvent?) = onChange(e)
	override fun changedUpdate(e: DocumentEvent?) = onChange(e)
}

fun configureRelease(currentSemVarStr: String): ReleaseInfo? {
	var result: ReleaseInfo? = null

	val curSemVar = parseSemVar(currentSemVarStr)

	val windowClosedSignal = CountDownLatch(1)
	var newSemVar = curSemVar.incrementForRelease(SemVar.ReleaseType.MINOR)

	System.setProperty("java.awt.headless", "false")
	SwingUtilities.invokeAndWait {
		val frame = JFrame("Prepare Release")
		frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
		frame.setSize(600, 600)

		val panel = JPanel()
		val boxLayout = BoxLayout(panel, BoxLayout.Y_AXIS)
		panel.layout = boxLayout

		panel.add(
			JLabel("Current Version: $curSemVar").apply {
				setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
			}
		)

		panel.add(JLabel("What type of release is this?:"))

		val optionMajor = JRadioButton("Major")
		val optionMinor = JRadioButton("Minor")
		val optionPatch = JRadioButton("Patch")
		val group = ButtonGroup()
		group.add(optionMajor)
		group.add(optionMinor)
		group.add(optionPatch)

		optionMinor.isSelected = true

		val releaseOptions = JPanel()
		val radiobuttonLayout = BoxLayout(releaseOptions, BoxLayout.X_AXIS)
		releaseOptions.layout = radiobuttonLayout

		releaseOptions.add(optionMajor)
		releaseOptions.add(optionMinor)
		releaseOptions.add(optionPatch)

		panel.add(releaseOptions)

		panel.add(JLabel("New Version:"))

		val newVersionLabel = JLabel(newSemVar.toString()).apply {
			setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
		}
		panel.add(newVersionLabel)

		optionMajor.addActionListener { e ->
			newSemVar = curSemVar.incrementForRelease(SemVar.ReleaseType.MAJOR)
			newVersionLabel.text = newSemVar.toString()
		}
		optionMinor.addActionListener { e ->
			newSemVar = curSemVar.incrementForRelease(SemVar.ReleaseType.MINOR)
			newVersionLabel.text = newSemVar.toString()
		}
		optionPatch.addActionListener { e ->
			newSemVar = curSemVar.incrementForRelease(SemVar.ReleaseType.PATCH)
			newVersionLabel.text = newSemVar.toString()
		}

		val commitButton = JButton("Commit Changes")

		val characterCount = JLabel("Characters: 0")

		panel.add(JLabel("Change Log:"))
		val changeLog = JTextArea().apply {
			setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
			lineWrap = true
			wrapStyleWord = true
		}

		changeLog.document.addDocumentListener(OnChangeListener { e ->
			characterCount.text = "Characters: ${changeLog.document.length}"
		})

		panel.add(changeLog)
		panel.add(characterCount)
		panel.add(commitButton)

		commitButton.addActionListener {
			result = ReleaseInfo(
				semVar = newSemVar,
				changeLog = changeLog.text,
			)

			// Handle button click.
			frame.dispose()
		}

		frame.add(panel)
		frame.addWindowListener(object : WindowAdapter() {
			override fun windowClosed(e: WindowEvent) {
				windowClosedSignal.countDown()
			}
		})

		frame.isVisible = true
	}
	windowClosedSignal.await()

	return result
}
