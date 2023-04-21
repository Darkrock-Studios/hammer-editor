package com.darkrockstudios.apps.hammer.plugins.kweb

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kweb.*
import kweb.components.Component
import kweb.plugins.fomanticUI.fomantic
import kweb.routing.RouteReceiver
import kweb.state.KVar
import kweb.state.render

fun RouteReceiver.adminPanelPage(
	accountRepository: AccountsRepository,
	authToken: KVar<String?>,
	whiteListRepository: WhiteListRepository,
	scope: CoroutineScope,
	goTo: (String) -> Unit
) {
	path("/admin/{userId}") { params ->
		val userId = params.getValue("userId").value.toLong()

		// Enforce auth
		runBlocking {
			val isAdmin = accountRepository.getAccount(userId).isAdmin
			if (accountRepository.checkToken(userId, authToken.value ?: "").isFailure || isAdmin.not()) {
				goTo("/admin")
			}
		}

		div(fomantic.ui.middle.aligned.center.aligned.grid) {
			adminCard(userId, authToken, accountRepository, whiteListRepository, scope, goTo)
		}.addClasses("centered-container")
	}
}

private fun Component.adminCard(
	userId: Long,
	authToken: KVar<String?>,
	accountRepository: AccountsRepository,
	whiteListRepository: WhiteListRepository,
	scope: CoroutineScope,
	goTo: (String) -> Unit
) {
	val account = KVar<Account?>(null)
	//val list = ObservableList<String>()
	val list = KVar<List<String>>(emptyList())
	var initialUseWhiteList: Boolean

	suspend fun updateList() {
		//list.clear()
		//list.addAll(whiteListRepository.getWhiteList())
		val updated = whiteListRepository.getWhiteList()
		list.value = updated
	}

	runBlocking {
		updateList()
		//list.addAll(whiteListRepository.getWhiteList())

		account.value = accountRepository.getAccount(userId)
		initialUseWhiteList = whiteListRepository.useWhiteList()
	}

	div(fomantic.center.aligned.column) {
		div(fomantic.ui.card) {
			panelHeader(account, authToken, goTo)

			enableWhiteList(initialUseWhiteList, whiteListRepository, scope)

			whiteList(list, whiteListRepository, scope, ::updateList)

			addToWhiteList(whiteListRepository, scope, ::updateList)
		}
	}.addClasses("medium-width")
}

private fun Component.panelHeader(account: KVar<Account?>, authToken: KVar<String?>, goTo: (String) -> Unit) {
	div(fomantic.ui.top.attached.inverted.menu) {
		div(fomantic.header.item).innerHTML("Admin")
		div(fomantic.right.menu) {
			a(fomantic.header.item).innerHTML("X")
				.on.click {
					authToken.value = null
					goTo("/admin")
				}
		}
	}

	div(fomantic.left.aligned.content) {
		div(fomantic.meta) {
			h4().text(account.map { it?.email ?: "" })
		}
	}
}

private fun Component.enableWhiteList(
	initialUseWhiteList: Boolean,
	whiteListRepository: WhiteListRepository,
	scope: CoroutineScope
) {
	div(fomantic.left.aligned.content) {
		div(fomantic.ui.checkbox) {
			input(type = InputType.checkbox)
				.checked(initialUseWhiteList)
				.addListener { _, new ->
					scope.launch {
						whiteListRepository.setWhiteListEnabled(new)
					}
				}

			label().text("Enforce White List")
		}
	}
}

private fun Component.whiteList(
	//list: ObservableList<String>,
	list: KVar<List<String>>,
	whiteListRepository: WhiteListRepository,
	scope: CoroutineScope,
	updateList: suspend () -> Unit
) {
	div(fomantic.left.aligned.content) {
		h3().text("White List:")
		render(list) { rlist ->
			table(fomantic.ui.table) {
				thead {
					tr {
						th {
							span().text("Email")
						}
						th(fomantic.right.aligned) {
							span().text("Remove")
						}
					}
				}
				tbody {
					for (item in rlist) {
						whiteListEntry(item) {
							scope.launch {
								whiteListRepository.removeFromWhiteList(item)
								updateList()
							}
						}
					}
				}
			}
		}
	}
}

private fun Component.whiteListEntry(
	email: String,
	onClick: (String) -> Unit
) {
	tr(fomantic.content) {
		td {
			span().text(email)
		}
		td(fomantic.right.aligned) {
			div(fomantic.circular.negative.ui.icon.button) {
				i(fomantic.close.icon)
			}.on.click { onClick(email) }
		}
	}
}

private fun Component.addToWhiteList(
	whiteListRepository: WhiteListRepository,
	scope: CoroutineScope,
	updateList: suspend () -> Unit
) {
	val emailText = KVar("")

	div(fomantic.extra.centered.aligned) {
		label().text("Add to whtielist:")
		div(fomantic.ui.right.action.input) {
			input(
				attributes = fomantic.ui.labeled.input,
				type = InputType.text,
				placeholder = "EMail"
			).value = emailText

			div(fomantic.ui.teal.button) {
				i(fomantic.add.icon)
				label().text("Add")
			}.on.click {
				scope.launch {
					whiteListRepository.addToWhiteList(emailText.value)
					updateList()
					emailText.value = ""
				}
			}
		}
	}
}