package com.darkrockstudios.apps.hammer.android.wear

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.android.R
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead

private val DialogMaxWidth = 480.dp

/** [accountEmail] is null when this phone is not signed in to a sync server. */
@Composable
fun WearPairingDialog(
	deviceLabel: String,
	accountEmail: String?,
	onApprove: () -> Unit,
	onDecline: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Surface(
		modifier = modifier
			.widthIn(max = DialogMaxWidth)
			.fillMaxWidth(),
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant),
	) {
		Column {
			HdMasthead(
				section = stringResource(R.string.wear_pairing_masthead),
				leadingMeta = listOf(deviceLabel),
			)
			HdFolioDivider()
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(Ui.Padding.XL),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			) {
				if (accountEmail != null) {
					Text(
						text = stringResource(R.string.wear_pairing_title, deviceLabel),
						style = MaterialTheme.typography.titleMedium,
					)
					Text(
						text = stringResource(R.string.wear_pairing_body, accountEmail),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						HdHairlineButton(
							label = stringResource(R.string.wear_pairing_decline),
							onClick = onDecline,
						)
						HdHairlineButton(
							label = stringResource(R.string.wear_pairing_approve),
							emphasised = true,
							onClick = onApprove,
						)
					}
				} else {
					Text(
						text = stringResource(R.string.wear_pairing_not_signed_in),
						style = MaterialTheme.typography.bodyLarge,
					)
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.End,
					) {
						// Approving here answers the watch with NotSignedIn rather than a bare decline.
						HdHairlineButton(
							label = stringResource(R.string.wear_pairing_close),
							onClick = onApprove,
						)
					}
				}
			}
		}
	}
}
