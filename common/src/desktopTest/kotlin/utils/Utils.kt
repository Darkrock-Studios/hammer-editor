package utils

import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

fun <T : Any, R> T.callPrivate(methodName: String, vararg args: Any?): R {
    val privateMethod: KFunction<*>? =
        this::class.functions.find { t -> return@find t.name == methodName }

    val argList = args.toMutableList()
    (argList as ArrayList).add(0, this)
    val argArr = argList.toArray()

    if (privateMethod != null) {
        privateMethod.isAccessible = true
        return privateMethod.call(*argArr) as R
    } else {
        throw NoSuchMethodException("Method $methodName does not exist in ${this::class.qualifiedName}")
    }
}

fun <T : Any, R> T.getPrivateProperty(variableName: String): R {
    return this::class.memberProperties.find { it.name == variableName }?.let { field ->
        field.isAccessible = true
        val value = (field as KProperty1<Any, Any>).get(this) as R
        field.isAccessible = false
        return@let value
    } ?: throw IllegalArgumentException("Field not found: $variableName")
}