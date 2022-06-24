package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import kotlin.reflect.KClass

@Composable
actual fun painterResource(res: String, drawableKlass: Any?): Painter {
    val drawables = drawableKlass as KClass<Any>
    val id = drawableId(res, drawables)
    return androidx.compose.ui.res.painterResource(id)
}

// TODO: improve resource loading
fun drawableId(res: String, drawableKlass: KClass<Any>): Int {
    val imageName = res.substringAfterLast("/").substringBeforeLast(".")
    val field = drawableKlass.java.getDeclaredField(imageName)
    val idValue = field.get(drawableKlass) as Integer
    return idValue.toInt()
}