package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.plugins.kweb.KwebLocalizer
import com.github.aymanizz.ktori18n.R
import io.ktor.util.logging.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kweb.*
import kweb.components.Component
import kweb.plugins.fomanticUI.fomantic
import kweb.routing.RouteReceiver
import kweb.state.KVar

fun RouteReceiver.adminLoginPage(
	accountRepository: AccountsRepository,
	log: Logger,
	authToken: KVar<String?>,
	loc: KwebLocalizer,
	scope: CoroutineScope,
	goTo: (String) -> Unit
) {
	path("/admin") {
		val errorText = KVar("")
		val emailText = KVar("")
		val passwordText = KVar("")

		div(fomantic.ui.middle.aligned.center.aligned.grid) {
			div(fomantic.center.aligned.column) {
				div(fomantic.ui.card) {
					div(fomantic.content) {
						header(loc)

						loginFields(emailText, passwordText, errorText, loc)
					}

					loginButton(
						emailText,
						passwordText,
						authToken,
						accountRepository,
						log,
						loc,
						scope,
						{ errorText.value = it },
						goTo
					)
				}
			}.addClasses("medium-width")
		}.addClasses("centered-container")
	}
}

private fun Component.header(loc: KwebLocalizer) {
	div(fomantic.header) {
		h1().text(loc.t(R("admin.signin.header")))
	}
}

private fun Component.loginFields(
	emailText: KVar<String>,
	passwordText: KVar<String>,
	errorText: KVar<String>,
	loc: KwebLocalizer
) {
	div(fomantic.ui.description) {
		form(fomantic.ui.form) {
			div(fomantic.field) {
				label(fomantic.ui.label).text(loc.t("admin.signin.email"))
				input(type = InputType.text).value = emailText
			}

			div(fomantic.field) {
				label(fomantic.ui.label).text(loc.t("admin.signin.password"))
				input(type = InputType.password).value = passwordText
			}

			span(fomantic.ui.red.error.text).text(errorText)
		}
	}
}

private fun Component.loginButton(
	emailText: KVar<String>,
	passwordText: KVar<String>,
	authToken: KVar<String?>,
	accountRepository: AccountsRepository,
	log: Logger,
	loc: KwebLocalizer,
	scope: CoroutineScope,
	setError: (String) -> Unit,
	goTo: (String) -> Unit
) {
	div(fomantic.ui.one.bottom.attached.buttons) {
		button(fomantic.ui.button)
			.text(loc.t(R("admin.signin.loginbutton")))
			.on.click {
				scope.launch {
					val result = accountRepository.login(
						email = emailText.value,
						password = passwordText.value,
						installId = "web"
					)
					if (result.isSuccess) {
						log.info("login success!")
						val token = result.getOrThrow()
						authToken.value = token.auth
						goTo("/admin/${token.userId}")
					} else {
						setError(loc.t(R("admin.signin.loginfailure")))
					}
				}
			}
	}
}
