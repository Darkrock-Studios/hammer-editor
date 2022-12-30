package com.darkrockstudios.apps.hammer.common.di

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlIndentation
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig

internal fun createTomlSerializer(): Toml {
	return Toml(
		inputConfig = TomlInputConfig(
			// allow/prohibit unknown names during the deserialization, default false
			ignoreUnknownNames = false,
			// allow/prohibit empty values like "a = # comment", default true
			allowEmptyValues = true,
			// allow/prohibit null values like "a = null", default true
			allowNullValues = true,
			// allow/prohibit processing of empty toml, if false - throws an InternalDecodingException exception, default is true
			allowEmptyToml = true,
		),
		outputConfig = TomlOutputConfig(
			indentation = TomlIndentation.TAB,
		)
	)
}