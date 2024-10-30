package utils

import com.darkrockstudios.apps.hammer.common.util.StrRes
import dev.icerock.moko.resources.StringResource

class TestStrRes : StrRes {
	override fun get(str: StringResource) = "test"
	override fun get(str: StringResource, vararg args: Any) = "test"
}