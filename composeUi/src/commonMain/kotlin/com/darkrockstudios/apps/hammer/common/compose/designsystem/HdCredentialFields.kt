package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.settings_server_setup_password_hide
import com.darkrockstudios.apps.hammer.settings_server_setup_password_show

/**
 * Credential entry built on [HdHairlineField].
 *
 * Anything the user has to reproduce byte-for-byte on another device — a
 * password, the email it is keyed to — must reach the server exactly as typed.
 * These wrappers pin the keyboard options that guarantee that, so no screen has
 * to remember them: IME auto-capitalization and autocorrect are off, and the
 * password variant carries its own show/hide eye button.
 */
@Composable
fun HdPasswordField(
	label: String,
	value: String,
	onValueChange: (String) -> Unit,
	visible: Boolean,
	onVisibleChange: (Boolean) -> Unit,
	modifier: Modifier = Modifier,
	placeholder: String? = null,
	hint: String? = null,
	error: String? = null,
	imeAction: ImeAction = ImeAction.Done,
	enabled: Boolean = true,
	testTag: String? = null,
) {
	HdHairlineField(
		label = label,
		value = value,
		onValueChange = onValueChange,
		modifier = modifier,
		placeholder = placeholder,
		hint = hint,
		error = error,
		singleLine = true,
		imeAction = imeAction,
		capitalization = KeyboardCapitalization.None,
		autoCorrectEnabled = false,
		keyboardType = KeyboardType.Password,
		visualTransformation = if (visible) VisualTransformation.None
		else PasswordVisualTransformation(),
		enabled = enabled,
		testTag = testTag,
		trailing = {
			IconButton(onClick = { onVisibleChange(!visible) }, modifier = Modifier.size(32.dp)) {
				Icon(
					imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
					contentDescription = if (visible) {
						Res.string.settings_server_setup_password_hide.get()
					} else {
						Res.string.settings_server_setup_password_show.get()
					},
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.size(20.dp),
				)
			}
		},
	)
}

@Composable
fun HdEmailField(
	label: String,
	value: String,
	onValueChange: (String) -> Unit,
	modifier: Modifier = Modifier,
	placeholder: String? = null,
	hint: String? = null,
	error: String? = null,
	imeAction: ImeAction = ImeAction.Next,
	enabled: Boolean = true,
	testTag: String? = null,
) {
	HdHairlineField(
		label = label,
		value = value,
		onValueChange = onValueChange,
		modifier = modifier,
		placeholder = placeholder,
		hint = hint,
		error = error,
		singleLine = true,
		imeAction = imeAction,
		capitalization = KeyboardCapitalization.None,
		autoCorrectEnabled = false,
		keyboardType = KeyboardType.Email,
		enabled = enabled,
		testTag = testTag,
	)
}
