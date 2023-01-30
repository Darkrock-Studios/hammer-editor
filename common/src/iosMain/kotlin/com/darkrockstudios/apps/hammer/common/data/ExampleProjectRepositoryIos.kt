package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.globalsettings.GlobalSettingsRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual val exampleProjectModule = module {
	singleOf(::ExampleProjectRepositoryiOs) bind ExampleProjectRepository::class
}

class ExampleProjectRepositoryiOs(
	globalSettingsRepository: GlobalSettingsRepository,
) : ExampleProjectRepository(globalSettingsRepository) {
	override fun install() {
		TODO("Not yet implemented")
	}
}