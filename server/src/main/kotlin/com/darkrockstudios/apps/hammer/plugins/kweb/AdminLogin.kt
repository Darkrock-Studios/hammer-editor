package com.darkrockstudios.apps.hammer.plugins.kweb

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kweb.*
import kweb.components.Component
import kweb.plugins.fomanticUI.fomantic
import kweb.routing.RouteReceiver
import kweb.state.KVar

fun RouteReceiver.adminLoginPage(
	accountRepository: AccountsRepository,
	authToken: KVar<String?>,
	scope: CoroutineScope,
	goTo: (String) -> Unit
) {
	path("/admin") {
		val errorText = KVar("")
		val emailText = KVar("test@test.com")
		val passwordText = KVar("qweasdzxc")

		div(fomantic.ui.middle.aligned.center.aligned.grid.padded.fluid) {
			div(fomantic.ui.card) {
				div(fomantic.content) {
					header()

					loginFields(emailText, passwordText, errorText)
				}

				loginButton(
					emailText,
					passwordText,
					authToken,
					accountRepository,
					scope,
					{ errorText.value = it },
					goTo
				)
			}
		}
	}

}

private fun Component.header() {
	div(fomantic.header) {
		h1().text("Login")
	}
}

private fun Component.loginFields(emailText: KVar<String>, passwordText: KVar<String>, errorText: KVar<String>) {
	div(fomantic.ui.description) {
		form(fomantic.ui.form) {
			div(fomantic.field) {
				label(fomantic.ui.label).text("Email: ")
				input(type = InputType.text).value = emailText
			}

			div(fomantic.field) {
				label(fomantic.ui.label).text("Password: ")
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
	scope: CoroutineScope,
	setError: (String) -> Unit,
	goTo: (String) -> Unit
) {
	div(fomantic.ui.one.bottom.attached.buttons) {
		button(fomantic.ui.button)
			.text("Login")
			.on.click {
				scope.launch {
					val result = accountRepository.login(
						email = emailText.value,
						password = passwordText.value,
						installId = "web"
					)
					if (result.isSuccess) {
						println("login success!")
						val token = result.getOrThrow()
						authToken.value = token.auth
						goTo("/admin/${token.userId}")
					} else {
						setError("Failed to login")
					}
				}
			}
	}
}
