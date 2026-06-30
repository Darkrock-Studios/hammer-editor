//
//  SceneEditorWorkflowUITests.swift
//  iosUITests
//
//  Adapted from android/src/androidTest/.../SceneEditorWorkflowTest.kt. Creates a scene and
//  verifies it opens in the editor.
//
//  Note: unlike the Android test, this stops short of typing into the scene body and saving.
//  The scene body is a Compose rich-text editor that XCUITest cannot focus via a synthesized tap
//  (only auto-focused fields accept input on Compose-iOS), so driving the edit+save step isn't
//  reliably automatable. The create-and-open path is the meaningful smoke coverage we can get.
//

import XCTest

final class SceneEditorWorkflowUITests: HammerUITest {

    func testCreateSceneOpensEditor() {
        createAndOpenProject(baseName: "iOS-Scene")

        tap(Tag.navEditor)

        // Create a scene and open it.
        tap(Tag.sceneListAdd)
        tap(Tag.sceneListAddScene)

        // The scene-name dialog auto-focuses, so typing + IME submit works.
        type("Opening", into: Tag.createItemNameField)
        app.typeText("\n")

        // Creating a scene auto-opens it in the editor.
        waitFor(Tag.sceneEditorText)
    }
}
