//
//  LaunchSmokeUITests.swift
//  iosUITests
//
//  The most basic smoke test: the real app launches through the SwiftUI entry point,
//  Koin initialises, and the project-selection screen renders.
//

import XCTest

final class LaunchSmokeUITests: HammerUITest {

    func testAppLaunchesToProjectSelection() {
        // If this tag renders, the Swift wrapper booted, the Hammer framework loaded, Koin
        // initialised, data migration ran, and the first Compose frame drew.
        waitFor(Tag.createProjectButton)
    }
}
