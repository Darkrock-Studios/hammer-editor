package utils

import com.darkrockstudios.apps.hammer.common.util.StrRes
import org.jetbrains.compose.resources.StringResource

/** Records which string resources were asked for, with their arguments. */
class RecordingStrRes : StrRes {
	val calls = mutableListOf<Pair<StringResource, List<Any>>>()

	val requested: List<StringResource> get() = calls.map { it.first }

	fun argsOf(str: StringResource): List<List<Any>> = calls.filter { it.first == str }.map { it.second }

	override suspend fun get(str: StringResource): String {
		calls += str to emptyList()
		return "test"
	}

	override suspend fun get(str: StringResource, vararg args: Any): String {
		calls += str to args.toList()
		return "test"
	}
}
