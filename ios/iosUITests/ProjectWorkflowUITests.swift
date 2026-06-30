//
//  ProjectWorkflowUITests.swift
//  iosUITests
//
//  Onboarding smoke test: create a project and open it into the editor. iOS boots into project
//  selection (unlike the Android tests, which seed via Koin), so this exercises the real
//  create -> open flow end to end.
//

import XCTest

final class ProjectWorkflowUITests: HammerUITest {

    func testCreateAndOpenProject() {
        createAndOpenProject(baseName: "iOS-Smoke")
        // createAndOpenProject already asserts the project root rendered (nav-Home).
    }
}
