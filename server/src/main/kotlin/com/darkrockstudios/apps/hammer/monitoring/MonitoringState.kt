package com.darkrockstudios.apps.hammer.monitoring

/**
 * Cheap, in-memory mirror of the monitoring config flags that the hot paths
 * (e.g. the unhandled-error recorder) read on every request. The maintenance
 * job refreshes it from the persisted [MonitoringConfig] each tick, so we never
 * hit the database from a request handler just to check whether a feature is on.
 */
class MonitoringState {
	@Volatile
	var errorTrackingEnabled: Boolean = true
		private set

	fun update(config: MonitoringConfig) {
		errorTrackingEnabled = config.enabled && config.trackErrors
	}
}
