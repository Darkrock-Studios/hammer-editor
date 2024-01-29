package com.darkrockstudios.build

import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.CountDownLatch
import javax.swing.*

fun markdown() {
	val flavour = CommonMarkFlavourDescriptor()
	val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString("")
}

data class ReleaseInfo(
	val semVar: SemVar,
	val changeLog: String,
)

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

		panel.add(JLabel("Change Log:"))
		val changeLog = JTextArea().apply {
			setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
		}
		panel.add(changeLog)
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
