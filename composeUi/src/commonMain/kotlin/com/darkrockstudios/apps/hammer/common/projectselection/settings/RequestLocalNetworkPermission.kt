package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.runtime.Composable

/**
 * Requests the local-network permission needed to reach a self-hosted sync server on the LAN.
 * Apps targeting Android 17 (API 37) must hold ACCESS_LOCAL_NETWORK to connect to local addresses;
 * the grant is resolved while the server-setup dialog is open, before any connection is attempted.
 * No-op on platforms without this restriction.
 */
@Composable
expect fun RequestLocalNetworkPermission(show: Boolean)
