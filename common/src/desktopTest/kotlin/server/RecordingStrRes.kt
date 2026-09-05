package server

import com.darkrockstudios.apps.hammer.common.util.StrRes
import org.jetbrains.compose.resources.StringResource

/** Records which string resources a failure path asked for. */
internal class RecordingStrRes : StrRes {
	val requested = mutableListOf<StringResource>()

	override suspend fun get(str: StringResource): String {
		requested += str
		return "test"
	}

	override suspend fun get(str: StringResource, vararg args: Any): String {
		requested += str
		return "test"
	}
}
