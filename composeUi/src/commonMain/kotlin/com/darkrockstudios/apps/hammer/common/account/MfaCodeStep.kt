package com.darkrockstudios.apps.hammer.common.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.MfaChallenge
import com.darkrockstudios.apps.hammer.base.http.MfaMethod
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.settings_server_setup_mfa_code_explanation
import com.darkrockstudios.apps.hammer.settings_server_setup_mfa_code_label
import com.darkrockstudios.apps.hammer.settings_server_setup_mfa_recovery_explanation
import com.darkrockstudios.apps.hammer.settings_server_setup_mfa_recovery_label
import com.darkrockstudios.apps.hammer.settings_server_setup_mfa_recovery_placeholder
import com.darkrockstudios.apps.hammer.settings_server_setup_mfa_use_recovery

/**
 * Code entry for a pending second factor, shared by server setup and reauthentication. The
 * authenticator code is a digits-only mono field; the toggle swaps it for a recovery code
 * field when the challenge allows one.
 */
@Composable
fun MfaCodeStep(
	challenge: MfaChallenge,
	code: String,
	onCodeChange: (String) -> Unit,
	useRecoveryCode: Boolean,
	onUseRecoveryCodeChange: (Boolean) -> Unit,
	enabled: Boolean,
	modifier: Modifier = Modifier,
) {
	val recoveryAvailable = MfaMethod.RECOVERY in challenge.methods
	val recovery = useRecoveryCode && recoveryAvailable
	val codeStyle = MaterialTheme.typography.bodyLarge.copy(
		fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
		letterSpacing = 2.sp,
	)

	Column(
		modifier = modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		Text(
			text = if (recovery) {
				Res.string.settings_server_setup_mfa_recovery_explanation.get()
			} else {
				Res.string.settings_server_setup_mfa_code_explanation.get()
			},
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)

		if (recovery) {
			HdHairlineField(
				label = Res.string.settings_server_setup_mfa_recovery_label.get(),
				value = code,
				onValueChange = onCodeChange,
				placeholder = Res.string.settings_server_setup_mfa_recovery_placeholder.get(),
				singleLine = true,
				imeAction = ImeAction.Done,
				capitalization = KeyboardCapitalization.None,
				autoCorrectEnabled = false,
				keyboardType = KeyboardType.Ascii,
				textStyle = codeStyle,
				enabled = enabled,
			)
		} else {
			HdHairlineField(
				label = Res.string.settings_server_setup_mfa_code_label.get(),
				value = code,
				onValueChange = { onCodeChange(it.filter(Char::isDigit).take(MfaMethod.TOTP_DIGITS)) },
				counter = "${code.length}/${MfaMethod.TOTP_DIGITS}",
				singleLine = true,
				imeAction = ImeAction.Done,
				capitalization = KeyboardCapitalization.None,
				autoCorrectEnabled = false,
				keyboardType = KeyboardType.Number,
				textStyle = codeStyle,
				enabled = enabled,
			)
		}

		if (recoveryAvailable) {
			HdHairlineToggleRow(
				checked = useRecoveryCode,
				onCheckedChange = {
					onCodeChange("")
					onUseRecoveryCodeChange(it)
				},
				label = Res.string.settings_server_setup_mfa_use_recovery.get(),
				enabled = enabled,
			)
		}
	}
}
