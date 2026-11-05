package utils

import com.darkrockstudios.apps.hammer.common.util.StrRes
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/** Resolves each string to its key in angle brackets, followed by any format arguments. */
class KeyStrRes : StrRes {
	override suspend fun get(str: StringResource): String = "<${str.key}>"
	override suspend fun get(str: StringResource, vararg args: Any): String =
		if (args.isEmpty()) "<${str.key}>" else "<${str.key}> ${args.joinToString()}"

	override suspend fun get(str: PluralStringResource, quantity: Int, vararg args: Any): String =
		"<${str.key}:$quantity> ${args.joinToString()}"
}
