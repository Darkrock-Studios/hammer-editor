package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.plugins.kweb.KwebLocalizer
import com.darkrockstudios.apps.hammer.plugins.kweb.text
import com.github.aymanizz.ktori18n.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kweb.InputType
import kweb.a
import kweb.components.Component
import kweb.div
import kweb.h3
import kweb.h4
import kweb.i
import kweb.input
import kweb.label
import kweb.plugins.fomanticUI.fomantic
import kweb.routing.RouteReceiver
import kweb.span
import kweb.state.KVar
import kweb.state.render
import kweb.table
import kweb.tbody
import kweb.td
import kweb.th
import kweb.thead
import kweb.tr

fun RouteReceiver.adminPanelPage(
	accountRepository: AccountsRepository,
	authToken: KVar<String?>,
	whiteListRepository: WhiteListRepository,
	loc: KwebLocalizer,
	scope: CoroutineScope,
	goTo: (String) -> Unit
) {
	path("/admin/{userId}") { params ->
		val userId = params.getValue("userId").value.toLong()

		// Enforce auth
		runBlocking {
			val isAdmin = accountRepository.getAccount(userId).is_admin
			if (accountRepository.checkToken(userId, authToken.value ?: "").isFailure || isAdmin.not()) {
				goTo("/admin")
			}
		}

		div(fomantic.ui.middle.aligned.center.aligned.grid) {
			adminCard(userId, authToken, accountRepository, whiteListRepository, loc, scope, goTo)
		}.addClasses("centered-container")
	}
}

private fun Component.adminCard(
	userId: Long,
	authToken: KVar<String?>,
	accountRepository: AccountsRepository,
	whiteListRepository: WhiteListRepository,
	loc: KwebLocalizer,
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
			panelHeader(account, authToken, loc, goTo)

			enableWhiteList(initialUseWhiteList, whiteListRepository, loc, scope)

			whiteList(list, whiteListRepository, loc, scope, ::updateList)

			addToWhiteList(whiteListRepository, loc, scope, ::updateList)
		}
	}.addClasses("medium-width")
}

private fun Component.panelHeader(
	account: KVar<Account?>,
	authToken: KVar<String?>,
	loc: KwebLocalizer,
	goTo: (String) -> Unit
) {
	div(fomantic.ui.top.attached.inverted.menu) {
		div(fomantic.header.item).innerHTML(loc.t(R("admin.header")))
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
	loc: KwebLocalizer,
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

			label().text("admin.whitelist.checkbox", loc)
		}
	}
}

private fun Component.whiteList(
	//list: ObservableList<String>,
	list: KVar<List<String>>,
	whiteListRepository: WhiteListRepository,
	loc: KwebLocalizer,
	scope: CoroutineScope,
	updateList: suspend () -> Unit
) {
	div(fomantic.left.aligned.content) {
		h3().text("admin.whitelist.title", loc)
		render(list) { rlist ->
			table(fomantic.ui.table) {
				thead {
					tr {
						th {
							span().text(loc.t(R("admin.whitelist.colheader.email")))
						}
						th(fomantic.right.aligned) {
							span().text(loc.t(R("admin.whitelist.colheader.remove")))
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
	loc: KwebLocalizer,
	scope: CoroutineScope,
	updateList: suspend () -> Unit
) {
	val emailText = KVar("")

	div(fomantic.extra.centered.aligned) {
		label().text("admin.addtowhitelist.header", loc)
		div(fomantic.ui.right.action.input) {
			input(
				attributes = fomantic.ui.labeled.input,
				type = InputType.text,
				placeholder = loc.t(R("admin.addtowhitelist.hint.email"))
			).value = emailText

			div(fomantic.ui.teal.button) {
				i(fomantic.add.icon)
				label().text("admin.addtowhitelist.addbutton", loc)
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