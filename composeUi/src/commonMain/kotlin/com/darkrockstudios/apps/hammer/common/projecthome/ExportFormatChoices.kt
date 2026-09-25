package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.export.BuiltInExportFormat
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporterRegistry
import com.darkrockstudios.apps.hammer.project_home_export_format_docx
import com.darkrockstudios.apps.hammer.project_home_export_format_epub
import com.darkrockstudios.apps.hammer.project_home_export_format_markdown
import com.darkrockstudios.apps.hammer.project_home_export_format_pdf
import com.darkrockstudios.apps.hammer.project_home_export_format_rtf
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.koinInject

data class ExportFormatChoice(val formatId: String, val label: String)

private val builtInLabels: Map<String, StringResource> = mapOf(
	BuiltInExportFormat.EPUB to Res.string.project_home_export_format_epub,
	BuiltInExportFormat.DOCX to Res.string.project_home_export_format_docx,
	BuiltInExportFormat.RTF to Res.string.project_home_export_format_rtf,
	BuiltInExportFormat.PDF to Res.string.project_home_export_format_pdf,
	BuiltInExportFormat.MARKDOWN to Res.string.project_home_export_format_markdown,
)

/** A format with no label, built-in or its own, shows its file extension. */
@Composable
internal fun exportFormatChoices(exporters: List<StoryExporter>): List<ExportFormatChoice> = exporters.map { exporter ->
	val label = builtInLabels[exporter.formatId]?.get() ?: exporter.label
	ExportFormatChoice(exporter.formatId, label ?: exporter.fileExtension.uppercase())
}

@Composable
internal fun exportFormatChoices(): List<ExportFormatChoice> = exportFormatChoices(koinInject<StoryExporterRegistry>().exporters)
