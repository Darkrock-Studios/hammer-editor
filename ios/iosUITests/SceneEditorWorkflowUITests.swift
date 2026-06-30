//
//  SceneEditorWorkflowUITests.swift
//  iosUITests
//
//  Adapted from android/src/androidTest/.../SceneEditorWorkflowTest.kt. Creates a scene and
//  verifies it opens in the editor.
//
//  Note: unlike the Android test, this stops short of typing into the scene body and saving.
//  Text entry into the editor itself now works (the rich-text editor publishes text semantics as
//  of composetexteditor 2.3.0 — see NotesWorkflowUITests, which types into the note body). The
//  scene edit+save flow is deferred because the scene editor is gated on its initial buffer load
//  and its save affordance is dirty-driven, so an IME-driven edit doesn't reliably surface the
//  save action in time; promoting this to the full edit+save flow is a follow-up.
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
