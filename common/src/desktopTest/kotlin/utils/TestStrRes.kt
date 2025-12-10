package utils

import com.darkrockstudios.apps.hammer.common.util.StrRes
import org.jetbrains.compose.resources.StringResource

class TestStrRes : StrRes {
	override suspend fun get(str: StringResource) = "test"
	override suspend fun get(str: StringResource, vararg args: Any) = "test"
}