package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.data.projectsync.SyncLogMessage

@Composable
fun SyncLogMessageUi(logMsg: SyncLogMessage, showProjectName: Boolean = true) {
	Card(
		modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
		colors = CardDefaults.cardColors(
			containerColor = logMsg.getBackgroundColor(),
			contentColor = logMsg.getForegroundColor()
		)
	) {
		Row(
			modifier = Modifier.padding(4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon(
				logMsg.getIcon(),
				logMsg.level.name,
				modifier = Modifier.size(16.dp)
			)
			Spacer(modifier = Modifier.width(8.dp))
			if (showProjectName) {
				logMsg.projectName?.let {
					Text(
						it,
						fontWeight = FontWeight.Bold,
						textDecoration = TextDecoration.Underline
					)
					Spacer(modifier = Modifier.width(8.dp))
				}
			}
			SelectionContainer {
				Text(
					logMsg.message,
				)
			}
		}
	}
}