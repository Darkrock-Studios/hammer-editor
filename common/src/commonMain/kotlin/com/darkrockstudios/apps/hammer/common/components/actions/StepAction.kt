package com.darkrockstudios.apps.hammer.common.components.actions

/** Goes to whatever [target] picks; not live while it picks nothing. */
fun <T : Any> stepAction(target: () -> T?, go: (T) -> Unit): ActionHandler =
	ActionHandler(isEnabled = { target() != null }) { target()?.let(go) }

/** The item [offset] places from the first that [isCurrent] matches; null without one, or past either end. */
fun <T> List<T>.neighbour(offset: Int, isCurrent: (T) -> Boolean): T? {
	val index = indexOfFirst(isCurrent)
	if (index < 0) return null
	return getOrNull(index + offset)
}
