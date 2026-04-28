package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType

/**
 * Chip-label content for an encyclopedia entry reference: a small type icon
 * followed by the entry's name. Used inside reference chips in the scene
 * metadata panel and the sync conflict UI so both surfaces match visually.
 */
@Composable
fun EntryRefChipLabel(type: EntryType, name: String) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		Icon(
			imageVector = getEntryTypeIcon(type),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(16.dp),
		)
		Spacer(modifier = Modifier.size(4.dp))
		Text(name)
	}
}

/**
 * Variant for unresolvable reference IDs - used by the sync conflict UI when
 * the local client doesn't yet have the encyclopedia entry that one side of
 * the conflict references. A broken-link icon plus the raw `#id` so the user
 * knows something exists but isn't yet visible to them.
 */
@Composable
fun UnknownEntryRefChipLabel(id: Int) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		Icon(
			imageVector = Icons.Filled.LinkOff,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(16.dp),
		)
		Spacer(modifier = Modifier.size(4.dp))
		Text("#$id")
	}
}
