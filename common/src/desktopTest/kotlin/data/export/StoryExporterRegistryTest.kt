package data.export

import com.darkrockstudios.apps.hammer.common.data.export.BuiltInExportFormat
import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporterRegistry
import okio.BufferedSink
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StoryExporterRegistryTest {

	private class FakeExporter(override val formatId: String) : StoryExporter {
		override val fileExtension = "txt"
		override val mimeType = "text/plain"
		override fun render(sink: BufferedSink, input: ExportInput) = Unit
	}

	@Test
	fun `built-in formats come first in menu order, then contributed formats by id`() {
		val registry = StoryExporterRegistry(listOf(FakeExporter("zz.one"), FakeExporter("aa.two")))

		assertEquals(
			listOf(
				BuiltInExportFormat.EPUB,
				BuiltInExportFormat.DOCX,
				BuiltInExportFormat.RTF,
				BuiltInExportFormat.PDF,
				BuiltInExportFormat.MARKDOWN,
				"aa.two",
				"zz.one",
			),
			registry.exporters.map { it.formatId },
		)
	}

	@Test
	fun `looks up a format by id`() {
		val contributed = FakeExporter("aa.two")
		val registry = StoryExporterRegistry(listOf(contributed))

		assertSame(contributed, registry.forFormat("aa.two"))
		assertEquals("md", registry.forFormat(BuiltInExportFormat.MARKDOWN).fileExtension)
	}

	@Test
	fun `a contributed format cannot reuse an existing id`() {
		assertThrows<IllegalArgumentException> { StoryExporterRegistry(listOf(FakeExporter(BuiltInExportFormat.PDF))) }
	}
}
