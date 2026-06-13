package com.darkrockstudios.apps.hammer.review

import com.github.mustachejava.DefaultMustacheFactory
import java.io.StringWriter
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

/** Shared bundle/template plumbing for the review mailers. */
internal object ReviewMailTemplates {
	private val mustacheFactory = DefaultMustacheFactory("templates")

	/** English first as the complete baseline, the locale's translations on top. */
	fun loadMessages(locale: Locale): Map<String, String> {
		fun bundleMap(l: Locale): Map<String, String> {
			val bundle = ResourceBundle.getBundle("i18n.Messages", l)
			return bundle.keys.asSequence().associateWith { key -> bundle.getString(key) }
		}
		return if (locale.language == Locale.ENGLISH.language) {
			bundleMap(Locale.ENGLISH)
		} else {
			bundleMap(Locale.ENGLISH) + bundleMap(locale)
		}
	}

	fun format(messages: Map<String, String>, key: String, vararg args: Any): String {
		val raw = messages[key] ?: key
		return if (args.isEmpty()) raw else MessageFormat.format(raw, *args)
	}

	fun render(templatePath: String, model: Map<String, Any?>): String {
		val mustache = mustacheFactory.compile(templatePath)
		val writer = StringWriter()
		mustache.execute(writer, model)
		return writer.toString()
	}
}
