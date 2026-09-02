package com.darkrockstudios.apps.hammer.common.data.changelog

/**
 * Whether the in-app "What's New" dialog may be shown at all.
 *
 * False wherever Apple reviews the build. The baked changelog is the full release
 * entry, so it names Android and other platforms, which draws guideline 2.3.10
 * rejections.
 */
expect val supportsInAppChangelog: Boolean
